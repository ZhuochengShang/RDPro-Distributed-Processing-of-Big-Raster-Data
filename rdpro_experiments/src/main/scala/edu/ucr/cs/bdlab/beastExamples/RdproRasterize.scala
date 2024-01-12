package edu.ucr.cs.bdlab.beastExamples

import edu.ucr.cs.bdlab.beast._
import edu.ucr.cs.bdlab.beast.cg.SpatialDataTypes.RasterRDD
import edu.ucr.cs.bdlab.beast.common.{BeastOptions, CLIOperation}
import edu.ucr.cs.bdlab.beast.generator.{GaussianDistribution, SpatialGenerator, UniformDistribution}
import edu.ucr.cs.bdlab.beast.geolite.{EnvelopeNDLite, IFeature, ITile, RasterMetadata}
import edu.ucr.cs.bdlab.beast.util.OperationMetadata
import edu.ucr.cs.bdlab.raptor.{GeoTiffWriter, RasterOperationsGlobal, RasterOperationsLocal}
import org.apache.commons.math3.util.FastMath.{exp, log, pow}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.locationtech.jts.geom.Envelope

import java.awt.geom.{AffineTransform, Point2D}
import java.io.File

@OperationMetadata(
  shortName = "rdproraster",
  description = "Computes rasterize",
  inputArity = "2",
  outputArity = "1"
)
object RdproRasterize extends CLIOperation{
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={
    val conf = new SparkConf().setAppName(" Pixel Rasterize Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val checkRaster = outputs(0)
      val outputFileCompat = new File(checkRaster)
      val e = new Envelope(-180,180,-90,90)
      val rasterW = inputs(0).toInt // 12000 // 25000 // 40000
      val rasterH = inputs(1).toInt // 12000 // 20000 // 30000
      val cardinality = rasterW * rasterH
      val randomPoints: RDD[IFeature] = sc.generateSpatialData
        .distribution(UniformDistribution)
        .config(SpatialGenerator.Dimensions, 2)
        .config(UniformDistribution.GeometryType, "point")
        .mbr(new EnvelopeNDLite(e)) // fill entire space
        .generate(cardinality)
      randomPoints.count()
      val endRandPoint = System.nanoTime()

      // World range
      val x1 = -180
      val y1 =  90
      val x2 = 180
      val y2 = -90
      val metadata = RasterMetadata.create(x1,y1,x2,y2,4326,rasterW,rasterH,256,256)

      val valuedPixels: RDD[(Double,Double,Int)] = randomPoints.map(points=>new Tuple3(
        points.getGeometry.getCoordinate.getX,
        points.getGeometry.getCoordinate.getY,1))
      valuedPixels.count()
      val endArrayTime = System.nanoTime()

      val startRasterize = System.nanoTime()
      val outputRaster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePoints(valuedPixels,metadata)
      // write output to check
      outputRaster.foreach(item => {
        item.getPixelValue(item.x1, item.y1)
      })
      val endrasterizeTime = System.nanoTime();
      outputRaster.saveAsGeoTiff(outputFileCompat.getPath, Seq(GeoTiffWriter.WriteMode -> "distributed",GeoTiffWriter.BitsPerSample -> "8"))
      val endReadTime: Long = System.nanoTime()
      println("Generate random point time :"+ (endRandPoint -  startReadTime) / 1E9)
      println("Convert with coordinate system time : "+ ((endArrayTime - endRandPoint) /1E9))

      println("Total time of rasterize pixels: " + (endrasterizeTime - startRasterize) / 1E9)
      println("Total time for write : "+(endReadTime - endrasterizeTime) / 1E9)

      val total = (endRandPoint -  startReadTime) + (endArrayTime - endRandPoint)
      println("Total time of rasterize operation of rasterRDD: " + ((endReadTime - startReadTime)-(total)) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
