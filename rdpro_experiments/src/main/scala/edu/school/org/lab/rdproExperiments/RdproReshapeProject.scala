package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.cg.Reprojector
import edu.school.org.lab.rdpro.cg.Reprojector.{TransformationInfo, findTransformationInfo, reprojectEnvelope, reprojectGeometry}
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.{GeometryReader, ITile, RasterMetadata}
import edu.school.org.lab.rdpro.util.OperationMetadata
import edu.school.org.lab.raptor.RasterFileRDD.SPLIT_MAXSIZE
import edu.school.org.lab.raptor.{GeoTiffWriter, PixelScale, RasterOperationsFocal, RasterPartitioner}
import org.apache.spark.rdpro.CRSServer
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.geotools.referencing.CRS
import org.geotools.referencing.factory.OrderedAxisAuthorityFactory
import org.geotools.util.factory.Hints
import org.locationtech.jts.geom.{Coordinate, CoordinateSequence, Coordinates, Envelope, Geometry, GeometryFactory, Point}

import java.awt.geom.Point2D
import java.io.File
import java.util.logging.Logger
import scala.math.Numeric.IntIsIntegral

@OperationMetadata(
  shortName = "rdprocrs",
  description = "Computes reshape reproject",
  inputArity = "1",
  outputArity = "1"
)
object RdproReshapeProject extends CLIOperation{
  var factory: GeometryFactory = GeometryReader.DefaultGeometryFactory
  private val logger = Logger.getLogger("ProjectRaster")

  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={
    val conf = new SparkConf().setAppName("Reshape crs Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)
      val checkRaster = outputs(0)
      val outputFileDistributed = new File(checkRaster + ".dis.tif")

      val opts =  new BeastOptions()
      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster,0,opts)
      val targetCRS = CRS.decode("EPSG:4326")

      // start building target metadata
      val allMetadata: Array[RasterMetadata] = rasterRDDFile.allMetadata
      val initialMetadata = rasterRDDFile.first().rasterMetadata
      var minX1 = Double.MaxValue
      var minY1 = Double.MaxValue

      var maxX2 = Double.MinValue
      var maxY2 = Double.MinValue

      var minCellsizeX = Double.MaxValue
      var minCellsizeY = Double.MaxValue

      var rasterWidth = 0
      var rasterHeight = 0
      allMetadata.foreach( originMetadata => {
        val sourceCRS = CRSServer.sridToCRS(originMetadata.srid)

        val x1 = originMetadata.x1
        val y1 = originMetadata.y1
        val x2 = originMetadata.x2
        val y2 = originMetadata.y2
        val corners = Array[Double](x1, y1,
          x2, y1,
          x2, y2,
          x1, y2)
        originMetadata.g2m.transform(corners, 0, corners, 0, 4)

        val transform: TransformationInfo = findTransformationInfo(sourceCRS, targetCRS)
        transform.mathTransform.transform(corners, 0, corners, 0, 4)

        minX1 = minX1 min corners(0) min corners(2) min corners(4) min corners(6)
        maxX2 = maxX2 max corners(0) max corners(2) max corners(4) max corners(6)
        minY1 = minY1 min corners(1) min corners(3) min corners(5) min corners(7)
        maxY2 = maxY2 max corners(1) max corners(3) max corners(5) max corners(7)

        val col = originMetadata.rasterWidth
        val row = originMetadata.rasterHeight

        minCellsizeX = minCellsizeX min ((maxX2 - minX1).abs / col)
        minCellsizeY = minCellsizeY min ((maxY2 - minY1).abs / row)

      })

      rasterWidth = Math.floor(Math.abs(maxX2-minX1)/minCellsizeX).toInt
      rasterHeight = Math.floor(Math.abs(maxY2-minY1)/minCellsizeY).toInt

      val targetMetadata = RasterMetadata.create(minX1, maxY2, maxX2, minY1, 4326 , rasterWidth, rasterHeight, initialMetadata.tileWidth, initialMetadata.tileHeight)

      logger.info("Finish create metadata, start to call RESHAPENN : ")
      val CRS_only = RasterOperationsFocal.reshapeNN(rasterRDDFile, targetMetadata)
      CRS_only.foreach(
        tile => {
          tile.getPixelValue(tile.x1,tile.y1)
        }
      )


      CRS_only.saveAsGeoTiff(outputFileDistributed.getPath, Seq(GeoTiffWriter.WriteMode -> "distributed"))
      val endTime: Long = System.nanoTime()
      println("Total time of Reshape reproject of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }


}
