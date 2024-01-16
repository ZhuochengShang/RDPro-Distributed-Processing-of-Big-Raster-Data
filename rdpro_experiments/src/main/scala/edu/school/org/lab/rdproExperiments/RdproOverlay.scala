package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.{ITile, RasterMetadata}
import edu.school.org.lab.rdpro.util.OperationMetadata
import edu.school.org.lab.raptor.{GeoTiffWriter, RasterOperationsFocal, RasterOperationsLocal}
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.geotools.referencing.CRS

import java.awt.geom.Point2D
import java.io.File

@OperationMetadata(
  shortName = "rdprooverlay",
  description = "Computes overlay",
  inputArity = "2",
  outputArity = "0"
)
object RdproOverlay extends CLIOperation{
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={

    val conf = new SparkConf().setAppName("Overlay Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster_one = inputs(0) //  CDL
      val inputRaster_two = inputs(1) // Landsat8

      val reshapeRDDFile_cdl: RasterRDD[Int] = sc.geoTiff(inputRaster_one) // CDL
      val rasterRDDFile_lands: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster_two) //Landsat8
      val rasterRDDFile_lands_single: RasterRDD[Int] = rasterRDDFile_lands.mapPixels(bands=>bands(0))

      // reproject Landsat8 to the CDL CRS
      val targetMetadata = reshapeRDDFile_cdl.first().rasterMetadata
      val reshapedRaster_lands: RasterRDD[Int] = RasterOperationsFocal.reshapeNN(rasterRDDFile_lands_single, targetMetadata)

      val outputRaster: RasterRDD[Array[Int]] = RasterOperationsLocal.overlay( reshapedRaster_lands, reshapeRDDFile_cdl)
      outputRaster.foreach(item => {
        item.getPixelValue(item.x1, item.y1)
      })

      val endTime: Long = System.nanoTime()
      println("Total time of Overlay two raster of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
