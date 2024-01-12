package edu.ucr.cs.bdlab.beastExamples

import edu.ucr.cs.bdlab.beast._
import edu.ucr.cs.bdlab.beast.common.{BeastOptions, CLIOperation}
import edu.ucr.cs.bdlab.beast.geolite.ITile
import edu.ucr.cs.bdlab.beast.util.{OperationMetadata, OperationParam}
import edu.ucr.cs.bdlab.raptor.GeoTiffWriter
import org.apache.hadoop.fs.{Path, PathFilter}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import java.io.File
import java.util
import scala.collection.mutable

@OperationMetadata(
  shortName = "rdprowritecomp",
  description = "Computes write raster",
  inputArity = "1",
  outputArity = "1"
)
object RdproRasterWriterCompatability extends CLIOperation {
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
      val outputFileCompat = new File(outputRaster)

      val rasterRDDFile: RasterRDD[Array[Int]] = sc.geoTiff(inputRaster)
      GeoTiffWriter.saveAsGeoTiff(rasterRDDFile, outputFileCompat.getPath, Seq(GeoTiffWriter.WriteMode -> "compatibility"))
      val endWriteTime = System.nanoTime()

      println("Total time of read &  writing rasterRDD in compatability mode: " + ((endWriteTime - startReadTime)) / 1E9)
      println("------ %%% FINISHED %%% ------")
    } finally {
      spark.stop()
    }
  }

}
