package edu.school.org.lab.rdproExperiments

import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.common.{BeastOptions, CLIOperation}
import edu.school.org.lab.rdpro.util.OperationMetadata
import edu.school.org.lab.raptor.GeoTiffWriter
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

import java.io.File

@OperationMetadata(
  shortName = "rdprowritedist",
  description = "Computes write raster",
  inputArity = "1",
  outputArity = "1"
)
object RdproRasterWriterDistribute extends CLIOperation {
  def run(opts: BeastOptions, inputs: Array[String], outputs: Array[String], sc: SparkContext): Any = {

    val conf = new SparkConf().setAppName("Write Raster RDD")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc = spark.sparkContext
    val startReadTime: Long = System.nanoTime()
    try {
      val inputRaster = inputs(0)
      val outputRaster = outputs(0)
      val outputFileDistributed = new File(outputRaster)

      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster)
      GeoTiffWriter.saveAsGeoTiff(rasterRDDFile, outputFileDistributed.getPath, Seq(GeoTiffWriter.WriteMode -> "distributed"))
      val endWriteTime = System.nanoTime()

      println("Total time of read &  writing rasterRDD in distributed mode: " + ((endWriteTime - startReadTime)) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
