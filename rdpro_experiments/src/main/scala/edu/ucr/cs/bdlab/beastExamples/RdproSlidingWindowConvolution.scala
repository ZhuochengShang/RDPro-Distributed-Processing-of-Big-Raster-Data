package edu.ucr.cs.bdlab.beastExamples

import edu.ucr.cs.bdlab.beast._
import edu.ucr.cs.bdlab.beast.common.{BeastOptions, CLIOperation}
import edu.ucr.cs.bdlab.beast.geolite.GeometryReader
import edu.ucr.cs.bdlab.beast.util.OperationMetadata
import edu.ucr.cs.bdlab.raptor.{GeoTiffWriter, RasterOperationsFocal}
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.locationtech.jts.geom.GeometryFactory

import java.io.File

@OperationMetadata(
  shortName = "rdproconv",
  description = "Computes sliding window",
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
