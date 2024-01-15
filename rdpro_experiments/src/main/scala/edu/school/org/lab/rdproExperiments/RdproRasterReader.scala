package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.geolite.ITile
import edu.school.org.lab.rdpro.util.{OperationMetadata, OperationParam}
import edu.school.org.lab.rdpro.cg.SpatialDataTypes.RasterRDD
import org.apache.hadoop.fs.{Path, PathFilter}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession


import java.util
import scala.collection.mutable

@OperationMetadata(
  shortName = "rdproread",
  description = "Computes readraster",
  inputArity = "1",
  outputArity = "0"
)
object RdproRasterReader extends CLIOperation{
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={
    val conf = new SparkConf().setAppName("Read Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext

    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)

      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster)
      rasterRDDFile.foreach(tile => (tile.getPixelValue(tile.x1, tile.y1)))
      val endReadTime: Long = System.nanoTime()
      println("Total time of reading rasterRDD: " + (endReadTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
