/*
 * Copyright 2018 University of California, Riverside
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.school.org.lab.davinci

import edu.school.org.lab.davinci.MultilevelPyramidPlotHelper.TileClass
import edu.school.org.lab.rdpro.cg.CGOperationsMixin._
import edu.school.org.lab.rdpro.cg.Reprojector
import edu.school.org.lab.rdpro.cg.SpatialDataTypes.{JavaSpatialRDD, SpatialRDD}
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.{EmptyGeometry, EnvelopeNDLite, Feature, IFeature}
import edu.school.org.lab.rdpro.indexing.{IndexHelper, RSGrovePartitioner}
import edu.school.org.lab.rdpro.io.ReadWriteMixin._
import edu.school.org.lab.rdpro.io.{FeatureWriter, SpatialFileRDD, SpatialOutputFormat, SpatialWriter}
import edu.school.org.lab.rdpro.synopses
import edu.school.org.lab.rdpro.synopses.{AbstractHistogram, GeometryToPoints, HistogramOP, Prefix2DHistogram}
import edu.school.org.lab.rdpro.util._
import MultilevelPyramidPlotHelper.TileClass
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}
import org.apache.spark.api.java.JavaRDD
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.util.TaskFailureListener
import org.apache.spark.{HashPartitioner, SparkContext, TaskContext}
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.{Envelope, Geometry, TopologyException}

import java.io.IOException

/**
  * Creates a multilevel visualization using the flat partitioning algorithm. The input is split in a non-spatial way
  * into splits. Each split is visualized as a full pyramid which contains all the tiles but the tiles are not final
  * because they do not cover all the data. Finally, the partial tiles are merged to produce final tile images which
  * are then written to the output.
  */
@OperationMetadata(
  shortName =  "mplot",
  description = "Plots the input file as a multilevel pyramid image",
  inputArity = "1",
  outputArity = "1",
  inheritParams = Array(classOf[SpatialFileRDD], classOf[SpatialOutputFormat],
    classOf[CommonVisualizationHelper], classOf[MultilevelPyramidPlotHelper])
)
object MultilevelPlot extends CLIOperation with Logging {

  @OperationParam(description =
"""Write data tiles in the output directory next to images.
If false, data tiles are skipped and a link to the input path is added to the output _visualization.properties""",
    defaultValue = "true") val IncludeDataTiles = "data-tiles"

  /** The width of the tile in pixels */
  @OperationParam(description = "The width of the tile in pixels", defaultValue = "256")
  val TileWidth = "width"
  /** The height of the tile in pixels */
  @OperationParam(description = "The height of the tile in pixels", defaultValue = "256")
  val TileHeight = "height"

  /** The number of levels for multilevel plot */
  @OperationParam(description =
"""The total number of levels for multilevel plot.
Can be specified as a range min..max (inclusive of both) or as a single number which indicates the number of levels starting at zero."""",
    defaultValue = "7") val NumLevels = "levels"

  /** Data tile threshold. Any tile that is larger than this size is considered an image tile */
  @OperationParam(description =
    """Image tile threshold for adaptive multilevel plot.
    Any tile that is strictly larger than (not equal) this threshold is considered an image tile""", defaultValue = "1m")
  val ImageTileThreshold = "threshold"

  @OperationParam(
    description = "The minimum size of a tile consider it a large image tile. Must be larger than the image tile threshold.",
    defaultValue = "25m", showInUsage = false)
  @deprecated("No longer used when hybrid partitioning is enabled", "0.9.3")
  val LargeImageTileThreshold = "LargeImageTileThreshold"

  @OperationParam(
    description = "The deepest level to use with flat partitioning.",
    defaultValue = "10", showInUsage = false)
  @deprecated("No longer used when hybrid partitioning is enabled", "0.9.3")
  val DeepestFlatLevel = "DeepestFlatLevel"

  @OperationParam(description = "The maximum size for the histogram used in adaptive multilevel plot",
    defaultValue = "32m",
    showInUsage = false)
  val MaximumHistogramSize = "MultilevelPlot.MaxHistogramSize"

  @OperationParam(description = "Type of histogram used to classify tiles {simple, euler}", defaultValue = "simple")
  val HistogramType = "histogramtype"

  @OperationParam(description = "Concatenates all images tiles into one file to reduce num of files",
    defaultValue = "false", showInUsage = false)
  val ConcatenateImageTiles = "MultilevelPlot.CompactImageTiles"

  @OperationParam(description = "Uses one hybrid partitioner to create all image tiles",
    defaultValue = "true", showInUsage = false)
  val HybridPartitioning = "MultilevelPlot.HybridPartitioning"

  /**The name of the file that contains the hashtable of the tiles when the image tiles are concatenated*/
  val MasterTileFileName: String = "_tiles.hashtable"

  /**The file that contains concatenated image tiles*/
  val TilesFileName: String = "tiles"

  /** Java shortcut */
  def plotFeatures(features: JavaSpatialRDD, minLevel: Int, maxLevel: Int, plotterClass: Class[_ <: Plotter],
                   inputPath: String, outputPath: String, opts: BeastOptions): Unit =
    plotFeatures(features.rdd, minLevel to maxLevel, plotterClass, inputPath, outputPath, opts)

  /**
    * Plots the given features and writes the plot results to the output in a pyramid structure
    * @param features the set of features to plot
    * @param levels the range of levels to plot in the pyramid
    * @param plotterClass the plotter class used to generate the tiles
    * @param inputPath the input path. Used to add a link back to this path when the output is partial.
    * @param outputPath the output path where the tiles will be written.
    * @param opts user options for initializing the plotter and controlling the behavior of the operation
    */
  def plotFeatures(features: SpatialRDD, levels: Range, plotterClass: Class[_ <: Plotter],
                   inputPath: String, outputPath: String, opts: BeastOptions): Unit = {
    // Extract plot parameters
    val mbr = new EnvelopeNDLite(2)
    val maxHistogramSize = opts.getSizeAsBytes(MaximumHistogramSize, 32 * 1024 * 1024)
    val htype = Symbol(opts.getString(HistogramType, "simple").toLowerCase)
    val binSize = htype match {
      case 'simple => 8
      case 'euler => 32
    }
    val gridDimension = MultilevelPyramidPlotHelper.computeHistogramDimension(maxHistogramSize, levels.max, binSize)
    logInfo(s"Creating a histogram with dimensions $gridDimension x $gridDimension")

    var featuresToPlot: SpatialRDD = features
    // Check if vflip is enabled
    val mercator = opts.getBoolean(CommonVisualizationHelper.UseMercatorProjection, defaultValue = false)
    // Check if the Web Mercator option is enabled
    if (mercator) {
      // Web mercator is enabled
      // Apply mercator projection on all features
      featuresToPlot = VisualizationHelper.toWebMercator(featuresToPlot)
      // Enforce an MBR that covers the entire world in Mercator projection
      val worldMBR = new EnvelopeNDLite(
        Reprojector.reprojectEnvelope(CommonVisualizationHelper.MercatorMapBoundariesEnvelope, DefaultGeographicCRS.WGS84,
        CommonVisualizationHelper.MercatorCRS))
      mbr.set(worldMBR)
      opts.set(CommonVisualizationHelper.VerticalFlip, value = true)
    } else {
      // No web mercator. Compute the MBR of the input
      // Compute the MBR to know the bounds of the input region
      logInfo("Computing geometric summary")
      val summary = featuresToPlot.summary
      mbr.set(summary)
      // Expand the MBR to keep the ratio if needed
      if (opts.getBoolean(CommonVisualizationHelper.KeepRatio, defaultValue = true)) {
        // To keep the aspect ratio, expand the MBR to make it a square
        if (mbr.getSideLength(0) > mbr.getSideLength(1)) {
          val diff = mbr.getSideLength(0) - mbr.getSideLength(1)
          mbr.setMinCoord(1, mbr.getMinCoord(1) - diff / 2.0)
          mbr.setMaxCoord(1, mbr.getMaxCoord(1) + diff / 2.0)
        } else if (mbr.getSideLength(1) > mbr.getSideLength(0)) {
          val diff = mbr.getSideLength(1) - mbr.getSideLength(0)
          mbr.setMinCoord(0, mbr.getMinCoord(0) - diff / 2.0)
          mbr.setMaxCoord(0, mbr.getMaxCoord(0) + diff / 2.0)
        }
      }
    }
    opts.set("mbr", mbr.encodeAsString())
    val sc = featuresToPlot.context
    featuresToPlot = if (features.isSpatiallyPartitioned && featuresToPlot.getNumPartitions >= sc.defaultParallelism) {
      // Use them as is
      featuresToPlot
    } else {
      // Spatially repartition the data to increase efficiency
      val numPartitions = featuresToPlot.getNumPartitions max sc.defaultParallelism
      val partitionOpts = new BeastOptions(opts)
        // Disable balanced partitioning to make it more efficient
        .set(IndexHelper.BalancedPartitioning, value = false)
        .set(IndexHelper.PartitionCriterionThreshold, s"Fixed($numPartitions)")
      IndexHelper.partitionFeatures2(featuresToPlot, classOf[RSGrovePartitioner], _.getStorageSize, partitionOpts)
    }

    // Now, the parameters have been adjusted, start the actual visualization logic
    val threshold: Long = opts.getSizeAsBytes(ImageTileThreshold, "1m")
    val writeDataTiles: Boolean = opts.getBoolean(IncludeDataTiles,
      !(features.isSpatiallyPartitioned && features.isInstanceOf[SpatialFileRDD]))
    val tileWidth: Int = opts.getInt(TileWidth, 256)
    val tileHeight: Int = opts.getInt(TileHeight, 256)

    // Compute the uniform histogram and convert it to prefix sum for efficient sum of rectangles
    sc.setJobGroup("Histogram",
      s"Compute histogram of dimension $gridDimension x $gridDimension for visualization")
    logInfo(s"Computing a histogram of dimension $gridDimension x $gridDimension")
    val allPoints: SpatialRDD = featuresToPlot
      .flatMap(f => new GeometryToPoints(f.getGeometry))
      .map(g => Feature.create(null, g))
    val h: AbstractHistogram = if (threshold == 0) null else htype match {
        case 'simple => new Prefix2DHistogram(HistogramOP.computePointHistogramSparse(allPoints,
          _.getStorageSize, mbr, gridDimension, gridDimension))
        case 'euler => new synopses.PrefixEulerHistogram2D(HistogramOP.computeEulerHistogram(allPoints,
          _.getStorageSize, mbr, gridDimension, gridDimension))
      }

    val plotter: Plotter = Plotter.getConfiguredPlotter(plotterClass, opts)
    val bufferSize: Double = plotter.getBufferSize / tileWidth.toDouble

    val outPath: Path = new Path(outputPath)
    val outFS: FileSystem = outPath.getFileSystem(opts.loadIntoHadoopConf(null))

    var allImageTiles: Seq[RDD[Canvas]] = Seq()

    // Determine the deepest level with image tile to limit the depth of the partitioning
    val maxLevelImageTile: Int = if (threshold == 0) levels.max
      else MultilevelPyramidPlotHelper.findDeepestTileWithSize(h, threshold)
    logInfo(s"Maximum level with image tile $maxLevelImageTile")
    if (opts.getBoolean(HybridPartitioning, true)) {
      // Use hybrid partitioning
      val fullPartitioner = if (threshold == 0)
        new PyramidPartitioner(new SubPyramid(mbr, levels.min, maxLevelImageTile min levels.max))
      else
        new PyramidPartitioner(new SubPyramid(mbr, levels.min, maxLevelImageTile min levels.max), h, threshold + 1, Long.MaxValue)
      fullPartitioner.setBuffer(bufferSize)

      logInfo(s"Using hybrid plotting with partitioner $fullPartitioner")
      sc.setJobGroup("Image tiles", "Image tiles using hybrid partitioning " +
        s"in levels [${fullPartitioner.pyramid.getMinimumLevel},${fullPartitioner.pyramid.getMaximumLevel}] " +
        s"with sizes [${fullPartitioner.getMinThreshold},${fullPartitioner.getMaxThreshold})")
      allImageTiles = allImageTiles :+ createImageTilesHybrid(featuresToPlot, mbr, plotterClass,
        tileWidth, tileHeight, fullPartitioner, opts)
    } else {
      // Determine the deepest level with large image tile to decide which algorithm to use
      val thresholdLargeImage: Long = opts.getSizeAsBytes(LargeImageTileThreshold, "25m")
      val maxLevelLargeImageTile: Int = if (threshold == 0) levels.max
      else MultilevelPyramidPlotHelper.findDeepestTileWithSize(h, thresholdLargeImage)
      val deepestFlatLevel: Int = opts.getInt(DeepestFlatLevel, 10)

      logInfo(s"Maximum level with large image tile $maxLevelLargeImageTile")

      // Use a mix of flat and pyramid partitioning

      // There are four cases based on the values above
      // Case 1: threshold = 0 => No histogram is computed. Thus we cannot use any size threshold
      //   Use flat partitioning for levels [min, deepestFlatLevel], and
      //   pyramid partitioning for levels [deepestFlatLevels + 1, max]
      // Case 2: deepestFlatLevel >= maxLevelImageTile
      //   Use only flat partitioning for levels [min, maxLevelImageTile] to plot all image tiles
      // Case 3: deepestFlatLevel < maxLevelLargeImageTile
      //   Use flat partitioning for levels [min, maxLevelLargeImageTile] with threshold range [largeImageTile, Inf)
      //   Use pyramid partitioning for levels [min, maxLevelImageTile] with threshold range [threshold + 1, largeImageTile)
      // Case 4: Anything else. Handle similar to case 1 but use the thresholds and the histogram
      //   Use flat partitioning for levels [min, deepestFlatLevel], and
      //   pyramid partitioning for levels [deepestFlatLevels + 1, max]

      // 1- Create image tiles with flat partitioning (Always runs)
      val (flatPartitioner: PyramidPartitioner, pyramidPartitioner: PyramidPartitioner) =
        if (threshold == 0) {
          // Case 1: No threshold set. Consider all tiles
          logDebug("Case 1")
          (new PyramidPartitioner(new SubPyramid(mbr, levels.min, deepestFlatLevel min levels.max)),
            new PyramidPartitioner(new SubPyramid(mbr, (deepestFlatLevel + 1) max levels.min, levels.max)))
        } else if (deepestFlatLevel >= maxLevelImageTile) {
          // Case 2
          logInfo("Case 2 (deepest flat level ignored)")
          (new PyramidPartitioner(new SubPyramid(mbr, levels.min, maxLevelImageTile min levels.max), h, threshold + 1, Long.MaxValue),
            new PyramidPartitioner(new SubPyramid(mbr, 1, 0)))
        } else if (deepestFlatLevel < maxLevelLargeImageTile) {
          // Case 3
          logInfo("Case 3 (deepest flat level ignored)")
          (new PyramidPartitioner(new SubPyramid(mbr, levels.min, maxLevelLargeImageTile min levels.max), h, thresholdLargeImage, Long.MaxValue),
            new PyramidPartitioner(new SubPyramid(mbr, levels.min, maxLevelImageTile min levels.max), h, threshold + 1, thresholdLargeImage))
        } else { // Case 4
          logInfo("Case 4 (max level with large image tile ignored)")
          (new PyramidPartitioner(new SubPyramid(mbr, levels.min, deepestFlatLevel min levels.max), h, threshold + 1, Long.MaxValue),
            new PyramidPartitioner(new SubPyramid(mbr, (deepestFlatLevel + 1) max levels.min, maxLevelImageTile min levels.max), h, threshold + 1, Long.MaxValue))
        }

      if (!flatPartitioner.isEmpty) {
        flatPartitioner.setBuffer(bufferSize)
        sc.setJobGroup("Image tiles", "Image tiles using flat partitioning " +
          s"in levels [${flatPartitioner.pyramid.getMinimumLevel},${flatPartitioner.pyramid.getMaximumLevel}] " +
          s"with sizes in range [${flatPartitioner.getMinThreshold},${flatPartitioner.getMaxThreshold})")
        val imageTiles: RDD[Canvas] = createImageTilesWithFlatPartitioning(featuresToPlot, mbr, plotterClass,
          tileWidth, tileHeight, flatPartitioner, opts)
        allImageTiles = allImageTiles :+ imageTiles
      }

      if (!pyramidPartitioner.isEmpty) {
        pyramidPartitioner.setBuffer(bufferSize)
        sc.setJobGroup("Image tiles", "Image tiles using pyramid partitioning " +
          s"in levels [${pyramidPartitioner.pyramid.getMinimumLevel},${pyramidPartitioner.pyramid.getMaximumLevel}] " +
          s"with sizes [${pyramidPartitioner.getMinThreshold},${pyramidPartitioner.getMaxThreshold})")
        val imageTiles: RDD[Canvas] = createImageTilesWithPyramidPartitioning(featuresToPlot, mbr, plotterClass,
          tileWidth, tileHeight, pyramidPartitioner, opts)
        allImageTiles = allImageTiles :+ imageTiles
      }
    }

    // Write all image tiles
    if (outFS.exists(outPath) && opts.getBoolean(SpatialWriter.OverwriteOutput, defaultValue = false))
      outFS.delete(outPath, true)
    if (opts.getBoolean(ConcatenateImageTiles, defaultValue = false))
      saveImageTilesCompact(sc.union(allImageTiles), plotter, outPath.toString, opts)
    else
      saveImageTiles(sc.union(allImageTiles), plotter, outPath.toString, opts)

    // 3- Create data tiles if needed using pyramid partitioning
    if (writeDataTiles) {
      if (threshold > 0) {
        // 2- Create all data tiles using pyramid partitioning
        sc.setJobGroup("Data tiles", "Data tiles using pyramid partitioning")
        logInfo(s"Apply pyramid partitioning to generate all data tiles with size at most $threshold")
        val deepestLevelWithDataTile: Int = maxLevelImageTile + 1
        val fullPyramid = new SubPyramid(mbr, levels.min, Math.min(levels.max, deepestLevelWithDataTile))
        val dataTilePartitioner = if (h == null)
          new PyramidPartitioner(fullPyramid)
        else new PyramidPartitioner(fullPyramid, h, threshold, TileClass.DataTile)
        dataTilePartitioner.setBuffer(bufferSize)
        createDataTilesWithPyramidPartitioning(featuresToPlot, dataTilePartitioner, outputPath, opts)
      }
    } else {
      // Store the input path in the user configuration so it gets written to _visualization.properties file
      val outPath: Path = new Path(outputPath)
      val inPath: Path = new Path(inputPath)
      opts.set("data", FileUtil.relativize(inPath, outPath).toString)
    }

    // Combine all subdirectories into one directory
    opts.setClass(CommonVisualizationHelper.PlotterClassName, plotterClass, classOf[Plotter])
    opts.set(CommonVisualizationHelper.PlotterName, plotterClass.getAnnotation(classOf[Plotter.Metadata]).shortname())
    opts.set(NumLevels, rangeToString(levels))
    MultilevelPyramidPlotHelper.writeVisualizationAddOnFiles(outFS, outPath, opts)
  }

  /**
   * Save the given RDD of tiles to disk by writing each tile as a separate image.
   * @param tiles the set of tiles to write
   * @param plotter the plotter that will write canvases to disk
   * @param outPath the output path (directory) to write all tiles
   */
  def saveImageTiles(tiles: RDD[Canvas], plotter: Plotter, outPath: String, _opts: BeastOptions): Unit = {
    val opts = new BeastOptions(tiles.context.hadoopConfiguration).mergeWith(_opts)
    val plotterBC = tiles.sparkContext.broadcast(plotter)
    tiles.sparkContext.runJob(tiles, (context: TaskContext, canvases: Iterator[Canvas]) => {
      // Create a temporary directory for this task output
      val tempDir: Path = new Path(new Path(outPath), f"temp-${context.taskAttemptId()}")
      val outFS = tempDir.getFileSystem(opts.loadIntoHadoopConf())
      // Delete the temporary directory on task failure
      context.addTaskFailureListener(new TaskFailureListener() {
        override def onTaskFailure(context: TaskContext, error: Throwable): Unit = {
          if (outFS.exists(tempDir))
            outFS.delete(tempDir, true)
        }
      })
      outFS.mkdirs(tempDir)
      // Write all tiles
      val plotter = plotterBC.value
      val imgExt: String = plotter.getClass.getAnnotation(classOf[Plotter.Metadata]).imageExtension()
      val vflip: Boolean = opts.getBoolean(CommonVisualizationHelper.VerticalFlip, defaultValue = true)
      val tempTileIndex: TileIndex = new TileIndex()
      for (canvas <- canvases) {
        if (canvas != null) {
          TileIndex.decode(canvas.tileID, tempTileIndex)
          if (vflip)
            tempTileIndex.y = ((1 << tempTileIndex.z) - 1) - tempTileIndex.y
          val imagePath = new Path(tempDir, s"tile-${tempTileIndex.z}-${tempTileIndex.x}-${tempTileIndex.y}$imgExt")
          val outFile = outFS.create(imagePath)
          plotter.writeImage(canvas, outFile, vflip)
          outFile.close()
        }
      }
      // Move all image files to the output directory
      val imageFiles = outFS.listStatus(tempDir)
      for (imageFile <- imageFiles) {
        val destPath = new Path(outPath, imageFile.getPath.getName)
        outFS.rename(imageFile.getPath, destPath)
      }
      outFS.delete(tempDir, true)
    })
  }

  /**
   * Save the given RDD of tiles to disk by writing each all tiles to a single file and adding a hashtable file
   * that points to where each tile is.
   * @param tiles the set of tiles to write
   * @param plotter the plotter that will write canvases to disk
   * @param outPath the output path (directory) to write the tiles file (single file) and hashtable file
   */
  def saveImageTilesCompact(tiles: RDD[Canvas], plotter: Plotter, outPath: String, _opts: BeastOptions): Unit = {
    val opts = new BeastOptions(tiles.context.hadoopConfiguration).mergeWith(_opts)
    val plotterBC = tiles.sparkContext.broadcast(plotter)
    val interimFiles: Array[(String, Array[(Long, (Long, Int))])] = tiles.sparkContext
      .runJob(tiles, (context: TaskContext, canvases: Iterator[Canvas]) => {
        // Create a temporary directory for this task output
        val tempDir: Path = new Path(outPath, f"temp-${context.taskAttemptId()}")
        val outFS = tempDir.getFileSystem(opts.loadIntoHadoopConf())
        // Delete the temporary directory on task failure
        context.addTaskFailureListener(new TaskFailureListener() {
          override def onTaskFailure(context: TaskContext, error: Throwable): Unit = {
            if (outFS.exists(tempDir))
              outFS.delete(tempDir, true)
          }
        })
        outFS.mkdirs(tempDir)
        // Write all tiles to a single file
        val plotter = plotterBC.value
        val imgExt: String = plotter.getClass.getAnnotation(classOf[Plotter.Metadata]).imageExtension()
        val imageFile: Path = new Path(tempDir, f"tiles-${context.partitionId()}%05d$imgExt")
        val vflip: Boolean = opts.getBoolean(CommonVisualizationHelper.VerticalFlip, defaultValue = true)
        val tempTileIndex: TileIndex = new TileIndex()
        val outFile: FSDataOutputStream = outFS.create(imageFile)
        val tileOffsets = new scala.collection.mutable.ArrayBuffer[(Long, (Long, Int))]()
        try {
          for (canvas <- canvases) {
            if (canvas != null) {
              TileIndex.decode(canvas.tileID, tempTileIndex)
              if (vflip)
                tempTileIndex.y = ((1 << tempTileIndex.z) - 1) - tempTileIndex.y
              val finalTileID: Long = TileIndex.encode(tempTileIndex.z, tempTileIndex.x, tempTileIndex.y)
              val tileOffset = outFile.getPos
              plotter.writeImage(canvas, outFile, vflip)
              val tileSize: Int = (outFile.getPos - tileOffset).toInt
              tileOffsets.append((finalTileID, (tileOffset, tileSize)))
            }
          }
        } finally {
          outFile.close()
        }
        // Move the output file from the temporary directory to the output directory
        val finalPath = new Path(outPath, imageFile.getName)
        outFS.rename(imageFile, finalPath)
        outFS.delete(tempDir, true)
        (finalPath.toString, tileOffsets.toArray)
      })
    // Combine all temporary Files into one
    val tilesPath = new Path(outPath, TilesFileName)
    val outFS = tilesPath.getFileSystem(opts.loadIntoHadoopConf())
    val fileSizes: Array[Long] = interimFiles.map(p => outFS.getFileStatus(new Path(p._1)).getLen)
    FileUtil.concat(outFS, tilesPath, interimFiles.map(p => new Path(p._1)):_*)
    // Write a hashtable that records the offsets of all tiles
    val totalNumTiles: Int = interimFiles.map(_._2.length).sum
    val tileOffsets = new Array[(Long, (Long, Int))](totalNumTiles)
    var numTiles: Int = 0
    var offset: Long = 0
    for (iFile <- interimFiles.indices) {
      val tileOffsetsInIFile = interimFiles(iFile)._2
      for ((tileId, (tileOffset, tileSize)) <- tileOffsetsInIFile) {
        tileOffsets(numTiles) = (tileId, (tileOffset + offset, tileSize))
        numTiles += 1
      }
      offset += fileSizes(iFile)
    }
    DiskTileHashtable.construct(outFS, new Path(outPath, MasterTileFileName), tileOffsets)
  }

  /** Java shortcut */
  @throws(classOf[IOException])
  def plotGeometries(geoms: JavaRDD[_ <: Geometry], minLevel: Int, maxLevel: Int, outPath: String,
                     opts: BeastOptions): Unit =
    plotGeometries(geoms.rdd, minLevel to maxLevel, outPath, opts)

  /**
    * Plots the given set of geometries and writes the generated image to the output directory. This function uses the
    * [[GeometricPlotter]] to plot the geometry of the shapes and if data tiles are written
    * to the output, it will use CSV file format by default.
 *
    * @param geoms the set of geometries to plot
    * @param levels the range of levels to generate
    * @param outPath the path to the output directory
    * @param opts additional options to customize the behavior of the plot
    */
  @throws(classOf[IOException])
  def plotGeometries(geoms: RDD[_ <: Geometry], levels: Range, outPath: String, opts: BeastOptions): Unit = {
    // Convert geometries to features to be able to use the standard function
    val features: SpatialRDD = geoms.map(g => Feature.create(null, g))

    // Set a default output format if not set
    if (opts.get(SpatialFileRDD.InputFormat) == null && opts.get(SpatialWriter.OutputFormat) == null)
      opts.set(SpatialWriter.OutputFormat, "wkt")

    opts.set(NumLevels, s"${levels.min}..${levels.max}")

    plotFeatures(features, levels, classOf[GeometricPlotter], null, outPath, opts)
  }

  /**
    * Create data tiles for the given set of features using the pyramid partitioning method. First, each record is
    * assigned to all data tiles that it overlaps with. Then, the records are grouped by the tile ID. Finally, the
    * records in each group (i.e., tile ID) are written to a separate file with the naming convention tile-z-x-y.
    * @param features the set of features
    * @param partitioner the pyramid partitioner that defines which tiles to plot
    * @param opts the user-defined options for the visualization command.
    */
  private[davinci] def createDataTilesWithPyramidPartitioning(features: SpatialRDD, partitioner: PyramidPartitioner,
                                                    output: String, opts: BeastOptions): Unit = {
    // Create a sub-pyramid that represents the tiles of interest
    val mbr = new EnvelopeNDLite(2)
    val partitionedFeatures: RDD[(Long, IFeature)] = features.flatMap(feature => {
      mbr.setEmpty()
      mbr.merge(feature.getGeometry)
      val matchedTiles: Array[Long] = partitioner.overlapPartitions(mbr)
      matchedTiles.map(pid => (pid, feature))
    }).sortByKey()

    // Write the final canvas to the output
    saveDataTiles(partitionedFeatures, output, opts)
  }

  /**
   * Save partitioned features into data tiles
   * @param partitionedFeatures features partitioned and sorted by tile ID
   * @param outPath the path to write data tiles to, each tile is written as a separate file
   * @param _opts the write options including the output format
   */
  def saveDataTiles(partitionedFeatures: RDD[(Long, IFeature)], outPath: String, _opts: BeastOptions): Unit = {
    val opts = new BeastOptions(partitionedFeatures.context.hadoopConfiguration).mergeWith(_opts)
    val featureWriterClass: Class[_ <: FeatureWriter] =
      SpatialWriter.getFeatureWriterClass(opts.getString(SpatialWriter.OutputFormat))
    partitionedFeatures.sparkContext.runJob(partitionedFeatures,
      (context: TaskContext, features: Iterator[(Long, IFeature)]) => {
        // Create a temporary directory for this task output
        val tempDir: Path = new Path(new Path(outPath), f"temp-${context.taskAttemptId()}")
        val hadoopConf = opts.loadIntoHadoopConf()
        val outFS = tempDir.getFileSystem(hadoopConf)
        // Delete the temporary directory on task failure
        context.addTaskFailureListener(new TaskFailureListener() {
          override def onTaskFailure(context: TaskContext, error: Throwable): Unit = {
            if (outFS.exists(tempDir))
              outFS.delete(tempDir, true)
          }
        })
        outFS.mkdirs(tempDir)
        // Write all tiles
        val fileExt: String = featureWriterClass.getAnnotation(classOf[FeatureWriter.Metadata]).extension()
        val vflip: Boolean = opts.getBoolean(CommonVisualizationHelper.VerticalFlip, true)
        val tempTileIndex: TileIndex = new TileIndex()
        var featureWriter: FeatureWriter = null
        var currentTileID: Long = 0
        for ((tileID, feature) <- features) {
          if (tileID != currentTileID) {
            if (featureWriter != null)
              featureWriter.close()
            else
              featureWriter = featureWriterClass.getConstructor().newInstance()
            TileIndex.decode(tileID, tempTileIndex)
            if (vflip)
              tempTileIndex.y = ((1 << tempTileIndex.z) - 1) - tempTileIndex.y;
            val filePath = new Path(tempDir, s"tile-${tempTileIndex.z}-${tempTileIndex.x}-${tempTileIndex.y}${fileExt}")
            featureWriter.initialize(filePath, hadoopConf)
            currentTileID = tileID
          }
          featureWriter.write(feature)
        }
        if (featureWriter != null)
          featureWriter.close()
        // Move data files to the output directory
        for (file <- outFS.listStatus(tempDir)) {
          val newFile = new Path(outPath, file.getPath.getName)
          outFS.rename(file.getPath, newFile)
        }
        outFS.delete(tempDir, true)
      })
  }

  /**
    * Use flat partitioning to plot all tiles in the given range of zoom levels. First, all the partitions are scanned
    * and a set of partial tiles are created based on the data in each partition. Then, these partial tiles are reduced
    * by tile ID so that all partial images for each tile are merged into one final tile. Finally, the final tiles
    * are written to the output using the naming convention tile-z-x-y
    * @param features the set of features to visualize as an RDD
    * @param featuresMBR the MBR of the features
    * @param plotterClass the class to use as a plotter
    * @param tileWidth the width of each tile in pixels
    * @param tileHeight the height of each tile in pixels
    * @param partitioner the pyramid partitioner that selects the tiles to plot using flat partitioning
    * @param opts any other used-defined options
    * @return the created tiles as an RDD of TileID and Canvas
   */
  @deprecated("Use createImageTilesHybrid", "0.9.3")
  private[davinci] def createImageTilesWithFlatPartitioning(features: SpatialRDD, featuresMBR: EnvelopeNDLite,
                                                   plotterClass: Class[_ <: Plotter], tileWidth: Int, tileHeight: Int,
                                                   partitioner: PyramidPartitioner, opts: BeastOptions): RDD[Canvas] = {
    val partitionerBroadcast = features.sparkContext.broadcast(partitioner)
    // Assign each feature to all the overlapping tiles and group by tile ID
    val featuresAssignedToTiles: RDD[(Long, IFeature)] = features.flatMap(feature => {
      val mbr = new EnvelopeNDLite(2)
      mbr.merge(feature.getGeometry)
      partitionerBroadcast.value.overlapPartitions(mbr).map(tileid => (tileid, feature))
    })
    val plotter: Plotter = Plotter.getConfiguredPlotter(plotterClass, opts)
    val partialTiles: RDD[(Long, Canvas)] = featuresAssignedToTiles.mapPartitions(partitionedFeatures =>
      new TileCreatorFlatPartiotioning(partitionedFeatures, featuresMBR, plotter, tileWidth, tileHeight))

    val finalTiles: RDD[(Long, Canvas)] = partialTiles.reduceByKey((c1, c2) => {
      plotter.merge(c1, c2)
      c1
    })
    finalTiles.map(_._2)
  }

  /**
    * Use pyramid partitioning to plot all the images in the given range of zoom levels. First, this method scans all
    * the features and assigns each feature to all image tiles that it overlaps with in the given range of zoom levels.
    * Then, the records are grouped by tile ID and the records in each group (i.e., tile ID) are plotted. Finally,
    * the created image tiles are written to the output using the naming convention tile-z-x-y.
    * This method should be used when the size of each image tile is small enough so that all records in a tile
    * can be grouped together in memory before plotting.
    * @param features the set of features to plot as RDD
    * @param featuresMBR the MBR of the features
    * @param plotterClass the class of the plotter to use
    * @param tileWidth the width of each tile in pixels
    * @param tileHeight the height of each tile in pixels
    * @param partitioner the pyramid partitioner that chooses which tiles to plot
    * @param opts user-defined options
    * @return an RDD of TileID and the canvas
   */
  @deprecated("Use createImageTilesHybrid", "0.9.3")
  private[davinci] def createImageTilesWithPyramidPartitioning(features: SpatialRDD, featuresMBR: EnvelopeNDLite,
                                                               plotterClass: Class[_ <: Plotter], tileWidth: Int,
                                                               tileHeight: Int, partitioner: PyramidPartitioner,
                                                               opts: BeastOptions): RDD[Canvas] = {
    val plotter: Plotter = Plotter.getConfiguredPlotter(plotterClass, opts)
    val partitionerBroadcast = features.sparkContext.broadcast(partitioner)
    // Assign each feature to all the overlapping tiles and group by tile ID
    val featuresAssignedToTiles: RDD[(Long, IFeature)] = features.flatMap(feature => {
      val mbr = new EnvelopeNDLite(2)
      mbr.merge(feature.getGeometry)
      val allMatches: Array[Long] = partitionerBroadcast.value.overlapPartitions(mbr)
      if (allMatches.length > 1000)
        logWarning(s"!!Matched ${allMatches.length} tiles in pyramid partitioning")
      if (allMatches.length < 100)
        allMatches.map(pid => (pid, feature))
      else {
        // When number of matches is too high, we clip the feature to each
        val tilembr = new Envelope()
        allMatches.map(tileid => {
          try {
            TileIndex.getMBR(featuresMBR, tileid, tilembr)
            val clippedGeometry = feature.getGeometry.intersection(feature.getGeometry.getFactory.toGeometry(tilembr))
            (tileid, Feature.create(feature, clippedGeometry))
          } catch {
            case _: TopologyException => (tileid, Feature.create(null, EmptyGeometry.instance))
          }
        })
      }
    })

    // Repartition and sort within partitions
    val finalCanvases: RDD[Canvas] = featuresAssignedToTiles.repartitionAndSortWithinPartitions(new HashPartitioner(features.getNumPartitions))
      .mapPartitions(sortedFeatures => new TileCreatorPyramidPartitioning(sortedFeatures, partitioner, plotter, tileWidth, tileHeight))
    finalCanvases
  }


  /**
   * Use pyramid partitioning to plot all the images in the given range of zoom levels. First, this method scans all
   * the features and assigns each feature to all image tiles that it overlaps with in the given range of zoom levels.
   * Then, the records are grouped by tile ID and the records in each group (i.e., tile ID) are plotted. Finally,
   * the created image tiles are written to the output using the naming convention tile-z-x-y.
   * This method should be used when the size of each image tile is small enough so that all records in a tile
   * can be grouped together in memory before plotting.
   * @param features the set of features to plot as RDD
   * @param featuresMBR the MBR of the features
   * @param plotterClass the class of the plotter to use
   * @param tileWidth the width of each tile in pixels
   * @param tileHeight the height of each tile in pixels
   * @param partitioner the pyramid partitioner that chooses which tiles to plot
   * @param opts user-defined options
   * @return an RDD of TileID and the canvas
   */
  private[davinci] def createImageTilesHybrid(features: SpatialRDD, featuresMBR: EnvelopeNDLite,
                                              plotterClass: Class[_ <: Plotter], tileWidth: Int,
                                              tileHeight: Int, partitioner: PyramidPartitioner,
                                              opts: BeastOptions): RDD[Canvas] = {
    val plotter: Plotter = Plotter.getConfiguredPlotter(plotterClass, opts)
    val partitionerBroadcast = features.sparkContext.broadcast(partitioner)
    features.context.setJobDescription(s"Used hybrid partitioner $partitioner")
    // Assign each feature to all the overlapping tiles and group by tile ID
    val featuresAssignedToTiles: RDD[(Long, IFeature)] = features.flatMap(feature => {
      val mbr = new EnvelopeNDLite(2)
      mbr.merge(feature.getGeometry)
      val par = partitionerBroadcast.value
      val matchedTiles = par.overlapPartitions(mbr)
      if (matchedTiles.length < 100) {
        // Small object matches a few tiles, assign it directly to all matching tiles
        matchedTiles.map(pid => (pid, feature))
      } else {
        // When number of matches is too high, we clip the feature to each tile and only replicate to matching ones
        val tilembr = new Envelope()
        matchedTiles.map(tileid => {
          try {
            TileIndex.getMBR(featuresMBR, tileid, tilembr)
            val clippedGeometry = feature.getGeometry.intersection(feature.getGeometry.getFactory.toGeometry(tilembr))
            (tileid, Feature.create(feature, clippedGeometry))
          } catch {
            // If an error happens while computing the intersection, just return an empty geometry
            case _: TopologyException => (tileid, Feature.create(null, EmptyGeometry.instance))
          }
        }).filter(!_._2.getGeometry.isEmpty)
      }
    })

    // Since the canvas is initialized differently for each key, we start with null and initialize for the first record
    val emptyCanvas = CanvasModified(null)
    // The aggregate by key function does not receive the key, so we replicate the key to each record since we need it
    featuresAssignedToTiles.map({ case (tileId, canvas) => (tileId, (tileId, canvas)) })
      .aggregateByKey(emptyCanvas) ((canvas, tileFeature) => {
        // Initialize canvas if not initialized
        val c = if (canvas.canvas == null) {
          val tilembr = new Envelope()
          TileIndex.getMBR(featuresMBR, tileFeature._1, tilembr)
          CanvasModified(plotter.createCanvas(tileWidth, tileHeight, tilembr, tileFeature._1))
        } else {
          canvas
        }
        // Plot the record
        c.modified = plotter.plot(c.canvas, tileFeature._2) || c.modified
        if (c.modified) c else emptyCanvas
      }, (canvas1, canvas2) => {
        if (!canvas2.modified) {
          canvas1
        } else if (!canvas1.modified) {
          canvas2
        } else {
          plotter.merge(canvas1.canvas, canvas2.canvas)
          canvas1.modified = canvas1.modified || canvas2.modified
          canvas1
        }
      })
      .map(cf => cf._2.canvas)
  }

  /**
    * A helper function parses a string into range. The string is in the following forms:
    * - 8: Produces the range [0, 8) exclusive of 8, i.e., [0,7]
    * - 3..6: Produces the range [3, 6], inclusive of both 3 and 4
    * - 4...7: Produces the range [4, 7), exclusive of the 7, i.e., [4, 6]
    * @param str the string to parse
    * @return the created range
    */
  def parseRange(str: String): Range = {
    val oneNumber = raw"(\d+)".r
    val inclusiveRange = raw"(\d+)..(\d+)".r
    val exclusiveRange = raw"(\d+)...(\d+)".r
    str match {
      case oneNumber(number) => 0 until number.toInt
      case inclusiveRange(start, end) => start.toInt to end.toInt
      case exclusiveRange(start, end) => start.toInt until end.toInt - 1
      case _ => throw new RuntimeException(s"Unrecognized range format $str. start..end is a supported format")
    }
  }

  def rangeToString(rng: Range): String = {
    if (rng.start == 0) {
      (rng.last + 1).toString
    } else {
      s"${rng.start}..${rng.last}"
    }
  }

  /**
    * Run the main function using the given user command-line options and spark context
    *
    * @param opts user options for the operation
    * @param sc the spark context to use
    * @return
    */
  @throws(classOf[IOException])
  override def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any = {
    val features: SpatialRDD = sc.spatialFile(inputs(0), opts)
    val levels: Range = parseRange(opts.getString(NumLevels, "7"))
    val plotterClass: Class[_ <: Plotter] = Plotter.getPlotterClass(opts.getString(CommonVisualizationHelper.PlotterName))
    plotFeatures(features, levels, plotterClass, inputs(0), outputs(0), opts)
  }
}
