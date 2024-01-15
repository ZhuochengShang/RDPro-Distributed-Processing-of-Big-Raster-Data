package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.util.OperationMetadata
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

import java.io.File

@OperationMetadata(
  shortName = "rdproflatten",
  description = "Computes flatten",
  inputArity = "1",
  outputArity = "0"
)
object RdproFlatten extends CLIOperation{
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any ={
    val conf = new SparkConf().setAppName("Flatten Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext

    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)

      val rasterRDDFile: RasterRDD[Int] = sc.geoTiff(inputRaster)
      val flattenRDDFile = rasterRDDFile.flatten
      val valueFlattenRDD = flattenRDDFile.map(item=>new Tuple2(item._4,null))
      val counts = valueFlattenRDD.countByKey()

      val endTime: Long = System.nanoTime()
      println("Total time of Flatten of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }
}
