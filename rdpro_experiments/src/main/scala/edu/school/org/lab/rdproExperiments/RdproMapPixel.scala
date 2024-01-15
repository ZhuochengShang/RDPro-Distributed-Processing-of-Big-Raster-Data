package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.ITile
import edu.school.org.lab.rdpro.util.{OperationMetadata, OperationParam}
import edu.school.org.lab.raptor.{GeoTiffWriter, RasterOperationsLocal}
import org.apache.hadoop.fs.{Path, PathFilter}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import java.io.File

@OperationMetadata(
  shortName = "rdpromappixel",
  description = "Computes map pixels",
  inputArity = "1",
  outputArity = "0"
)
object RdproMapPixel extends CLIOperation {
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any = {

    val conf = new SparkConf().setAppName("Map Pixel Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)

      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster)
      val mappedPixel: RasterRDD[Int] = rasterRDDFile.mapPixels(iterm => iterm(0) + iterm(1))
      mappedPixel.foreach(tile => (tile.getPixelValue(tile.x1, tile.y1)))

      val endTime: Long = System.nanoTime()
      println("Total time of computing MapPixel of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
