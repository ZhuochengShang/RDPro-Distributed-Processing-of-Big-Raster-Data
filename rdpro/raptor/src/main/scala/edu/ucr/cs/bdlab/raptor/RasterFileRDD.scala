/*
 * Copyright 2021 University of California, Riverside
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
package edu.ucr.cs.bdlab.raptor

import edu.ucr.cs.bdlab.beast.common.BeastOptions
import edu.ucr.cs.bdlab.beast.geolite.ITile
import edu.ucr.cs.bdlab.beast.util.{IConfigurable, OperationParam, Parallel2}
import org.apache.hadoop.fs.{FileStatus, FileSystem, Path, PathFilter}
import org.apache.spark.api.java.{JavaRDD, JavaSparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, SparkContext, TaskContext}

/**
 * An RDD partition for a RasterFileRDD. It represents part of a raster file.
 * @param index the ID of the partition
 * @param path the path to the file
 * @param offset the start offset to read from the file
 * @param length the length of the partition in bytes
 * @param locations list of hosts that has this partition
 */
case class RasterFilePartition(override val index: Int, path: String,
                               offset: Long, length: Long, locations: Array[String]) extends Partition {
  override def toString: String = s"Raster Partition #$index ($path: $offset+$length)"
}

/**
 * A raster RDD that reads tiles from a file
 */
class RasterFileRDD[T](@transient sc: SparkContext, path: String, @transient _opts: BeastOptions) extends RDD[ITile[T]](sc, Seq()) {
  import RasterFileRDD._

  lazy val minSplitSize: Long = opts.getSizeAsBytes(SPLIT_MINSIZE, 1)

  lazy val maxSplitSize: Long = opts.getSizeAsBytes(SPLIT_MAXSIZE, Long.MaxValue)

  /**Hadoop configuration loaded into BeastOptions to make it serializable*/
  val opts: BeastOptions = new BeastOptions(_opts).mergeWith(sc.getConf).mergeWith(new BeastOptions(sc.hadoopConfiguration))

  override protected def getPartitions: Array[Partition] = {
    val p = new Path(path)
    val fileSystem = p.getFileSystem(sc.hadoopConfiguration)
    val partitions = new collection.mutable.ArrayBuffer[RasterFilePartition]
    val rasterFiles: Array[String] = getRasterFiles(fileSystem, p)
    Parallel2.forEach(rasterFiles.length, (i1: Int, i2: Int) => {
      for (i <- i1 until i2) {
        val rasterFile = rasterFiles(i)
        val rasterPath: Path = new Path(rasterFile)
        val fileStatus: FileStatus = fileSystem.getFileStatus(rasterPath)
        val length: Long = fileStatus.getLen
        val splitSize: Long = fileStatus.getBlockSize max minSplitSize min maxSplitSize
        // If file not splittable.
        if (!isSplittable(rasterFile) || length == 0) {
          val locations = fileSystem.getFileBlockLocations(fileStatus, 0, length)
            .flatMap(l => l.getHosts.iterator).distinct
          partitions.synchronized {
            val partitionID = partitions.size
            partitions.append(RasterFilePartition(partitionID, rasterFile, 0, length, locations))
          }
        } else {
          var bytesRemaining: Long = length
          while (bytesRemaining / splitSize > SPLIT_SLOP) {
            val locations = fileSystem.getFileBlockLocations(fileStatus, length - bytesRemaining, splitSize)
              .flatMap(l => l.getHosts.iterator)
            partitions.synchronized {
              val partitionID = partitions.size
              partitions.append(RasterFilePartition(partitionID, rasterFile, length - bytesRemaining, splitSize, locations))
            }
            bytesRemaining -= splitSize
          }
          if (bytesRemaining != 0) {
            val locations = fileSystem.getFileBlockLocations(fileStatus, length - bytesRemaining, bytesRemaining)
              .flatMap(l => l.getHosts.iterator)
            partitions.synchronized {
              val partitionID = partitions.size
              partitions.append(RasterFilePartition(partitionID, rasterFile, length - bytesRemaining, bytesRemaining, locations))
            }
          }
        }
      }
    })
    logInfo(s"Generated ${partitions.size} partitions from raster input path '$path'")
    partitions.toArray
  }

  override protected def getPreferredLocations(split: Partition): Seq[String] =
    split.asInstanceOf[RasterFilePartition].locations

  private def isSplittable(rasterFilePath:String): Boolean = true

  override def compute(split: Partition, context: TaskContext): Iterator[ITile[T]] = {
    logDebug(s"Processing $split")
    val rasterPartition: RasterFilePartition = split.asInstanceOf[RasterFilePartition]
    val rasterPath = new Path(rasterPartition.path)
    val fileSystem = rasterPath.getFileSystem(opts.loadIntoHadoopConf())
    val rasterReader: IRasterReader[T] = RasterHelper.createRasterReader(fileSystem, rasterPath, opts)
    context.addTaskCompletionListener[Unit] { context =>
      // TODO: Update the bytes read before closing is to make sure lingering bytesRead statistics in
      // this thread get correctly added.
      // Close the reader of free up resources
      rasterReader.close()
    }
    val tileIds = rasterReader.getTileOffsets.zipWithIndex.filter(idpos => {
      idpos._1 >= rasterPartition.offset && idpos._1 < rasterPartition.offset + rasterPartition.length
    }).sortBy(x => (x._1)).map(_._2)
    tileIds.iterator.map(tid => {
      //println(s"Reading tile #${tid} at layer : ${rasterReader.getLayerName}")
      rasterReader.readTile(tid)
    })
//    new Iterator[ITile[T]]{
//      private var currentTileID: Int = -1
//
//      private def seekToNextTile(): Unit = {
//        do {
//          currentTileID += 1
//        } while (currentTileID < rasterReader.metadata.numTiles &&
//          (!rasterReader.isValidTile(currentTileID) ||
//            rasterReader.getTileOffset(currentTileID) < rasterPartition.offset ||
//            rasterReader.getTileOffset(currentTileID) >= rasterPartition.offset + rasterPartition.length))
//      }
//
//      seekToNextTile()
//
//      override def hasNext: Boolean = currentTileID < rasterReader.metadata.numTiles
//
//      override def next(): ITile[T] = {
//        val tile: ITile[T] = rasterReader.readTile(currentTileID)
//        seekToNextTile()
//        tile
//      }
//    }
  }
}

object RasterFileRDD extends IConfigurable {
  @OperationParam(description = "Maximum split size", showInUsage = false, defaultValue = "Long.MAX_VALUE")
  val SPLIT_MAXSIZE: String = "mapreduce.input.fileinputformat.split.maxsize"

  @OperationParam(description = "Minimum split size", showInUsage = false, defaultValue = "1")
  val SPLIT_MINSIZE: String = "mapreduce.input.fileinputformat.split.minsize"

  @OperationParam(description = "Whether to scan input path recursively", showInUsage = false, defaultValue = "false")
  val INPUT_DIR_RECURSIVE: String = "mapreduce.input.fileinputformat.input.dir.recursive"

  @OperationParam(description = "Number of threads to list input files", showInUsage = false, defaultValue = "1")
  val LIST_STATUS_NUM_THREADS: String = "mapreduce.input.fileinputformat.list-status.num-threads"

  val DEFAULT_LIST_STATUS_NUM_THREADS: Int = 1

  @OperationParam(description = "Input format for raster files",
    showInUsage = true)
  val RasterInputFormat: String = "rformat"

  private val SPLIT_SLOP: Double = 1.1 // 10% slop

  /**
   * Get raster files as a list of string
   * @param rasterFileSystem the file system that contains the raster files
   * @param rasterPath the path to a single file or a directory of raster files
   * @return list of raster files
   */
  private def getRasterFiles(rasterFileSystem: FileSystem, rasterPath: Path): Array[String] = {
    val rasterFiles: Array[String] = {
      if (rasterFileSystem.isDirectory(rasterPath)) {
        rasterFileSystem.listStatus(rasterPath, new PathFilter() {
          override def accept(path: Path): Boolean =
            path.getName.toLowerCase().endsWith(".tif") || path.getName.toLowerCase().endsWith(".hdf")
        }).map(p => p.getPath.toString)
      } else {
        Array(rasterPath.toString)
      }
    }
    rasterFiles
  }

  /**
   * Read a raster file as a JavaRDD
   * @param javaSparkContext the Java Spark context
   * @param path path to a file or a directory with files
   * @param opts additional options for loading the file
   * @return the created JavaRDD
   */
  def readRaster[T](javaSparkContext: JavaSparkContext, path: String, opts: BeastOptions): JavaRDD[ITile[T]] =
    JavaRDD.fromRDD(new RasterFileRDD(javaSparkContext.sc, path, opts))
}