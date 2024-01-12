package edu.ucr.cs.bdlab.beastExamples

import edu.ucr.cs.bdlab.beast._
import edu.ucr.cs.bdlab.beast.cg.SpatialDataTypes.RasterRDD
import edu.ucr.cs.bdlab.beast.common.{BeastOptions, CLIOperation}
import edu.ucr.cs.bdlab.beast.geolite.GeometryReader
import edu.ucr.cs.bdlab.beast.util.OperationMetadata
import edu.ucr.cs.bdlab.raptor.{GeoTiffWriter, RasterOperationsFocal}
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.locationtech.jts.geom.GeometryFactory

import java.io.File

@OperationMetadata(
  shortName = "rdprosldw",
  description = "Computes sliding window",
  inputArity = "1",
  outputArity = "1"
)
object RdproSlidingWindow extends CLIOperation{
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
      val smoothedRaster: RasterRDD[Float] = RasterOperationsFocal.slidingWindow2(repartitionRDD, 1, (values: Array[Int], defined) => {
        var sum: Int = 0
        var count: Int = 0
        for (i <- values.indices; if defined(i)) {
          sum += values(i)
          count += 1
        }
        sum.toFloat / count})
      smoothedRaster.foreach(tile => tile.getPixelValue(tile.x1, tile.y1))
      val endTime: Long = System.nanoTime()
      println("Total time of sliding window of rasterRDD: " + (endTime - startReadTime) / 1E9)
      println("------ %%% FINISHED %%% ------")
   } finally {
      spark.stop()
    }
  }

}
