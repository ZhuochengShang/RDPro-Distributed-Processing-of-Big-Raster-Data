package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.GeometryReader
import edu.school.org.lab.rdpro.util.OperationMetadata
import edu.school.org.lab.raptor.{GeoTiffWriter, RasterOperationsFocal}
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.locationtech.jts.geom.GeometryFactory

import java.io.File

@OperationMetadata(
  shortName = "rdproconv",
  description = "Computes sliding window convolution",
  inputArity = "1",
  outputArity = "1"
)
object RdproSlidingWindowConvolution extends CLIOperation{
  var factory: GeometryFactory = GeometryReader.DefaultGeometryFactory

  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={

    val conf = new SparkConf().setAppName("Sliding Window Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)

      val rasterRDDFile: RasterRDD[Int] = sc.geoTiff(inputRaster)
      val repartitionRDD = rasterRDDFile.repartition(144)
      val smoothedRaster: RasterRDD[Float] = RasterOperationsFocal.convolution(repartitionRDD, 1, Array.fill(9)(0.0f))
      smoothedRaster.foreach(tile => tile.getPixelValue(tile.x1,tile.y1))
      val endTime: Long = System.nanoTime()
      println("Total time of sliding window convolution of rasterRDD: " + (endTime - startReadTime) / 1E9) //very slow
      println("------ %%% FINISHED %%% ------")

    } finally {
      spark.stop()
    }
  }

}
