package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.util.OperationMetadata
import edu.school.org.lab.raptor.GeoTiffWriter
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

import java.io.File

@OperationMetadata(
  shortName = "rdprofilter",
  description = "Computes filter pixels",
  inputArity = "1",
  outputArity = "0"
)
object RdproFilterPixel extends CLIOperation{
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={
    val conf = new SparkConf().setAppName("Filter Pixel Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext

    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)

      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster)
      val filterRDD = rasterRDDFile.filterPixels(item=>(item(0)>100))
      filterRDD.foreach(tile => (tile.getPixelValue(tile.x1, tile.y1)(0)))

      val endTime: Long = System.nanoTime()

      println("Total time of FilterPixel of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
