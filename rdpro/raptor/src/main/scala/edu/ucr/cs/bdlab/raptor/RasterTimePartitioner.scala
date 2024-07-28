package edu.ucr.cs.bdlab.raptor

import edu.ucr.cs.bdlab.beast.geolite.{ITile, RasterFeature, RasterMetadata}
import org.apache.spark.Partitioner

import java.sql.Timestamp
import java.time.format.DateTimeFormatter

/**
 * partition by timestamp.
 * @param numPartitions the desired number of partitions to divide the space in
 */
class RasterTimePartitioner(val partitions: Int) extends Partitioner {
  override def numPartitions: Int = partitions
  override def getPartition(timeStamp: Any): Int = {
    val inputType =  timeStamp.asInstanceOf[(Timestamp, Int)]
    val inputTimeStamp = inputType._1
    val localDateTime = inputTimeStamp.toLocalDateTime
    (localDateTime.getHour + inputType._2.hashCode) % numPartitions
  }
}
