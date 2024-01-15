package edu.school.org.lab.rdpro.io

import edu.school.org.lab.rdpro.cg.SpatialPartitioner
import edu.school.org.lab.rdpro.geolite.IFeature
import edu.school.org.lab.rdpro.indexing.CellPartitioner
import edu.school.org.lab.rdpro.cg.SpatialPartitioner
import edu.school.org.lab.rdpro.common.BeastOptions
import edu.school.org.lab.rdpro.geolite.{EnvelopeNDLite, IFeature}
import edu.school.org.lab.rdpro.indexing.CellPartitioner
import edu.school.org.lab.rdpro.io.ReadWriteMixin._
import org.apache.spark.rdd.RDD
import org.apache.spark.test.ScalaSparkTest
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SpatialReaderScalaTest extends FunSuite with ScalaSparkTest {

  test("ReadIndexedFile") {
    val indexedPath = makeDirCopy("/test_index2").getPath
    val loadedFile: RDD[IFeature] = sparkContext.spatialFile(indexedPath, "wkt",
      new BeastOptions(false).set("separator", ";"))
    assert(loadedFile.count() == 3)
    assert(loadedFile.partitions.length == 1)
    assert(loadedFile.partitioner.isDefined)
    assert(loadedFile.partitioner.get.isInstanceOf[SpatialPartitioner])
    val mbr0 = new EnvelopeNDLite()
    loadedFile.partitioner.get.asInstanceOf[SpatialPartitioner]
      .asInstanceOf[CellPartitioner].getPartitionMBR(0, mbr0)
    assert(mbr0.getMaxCoord(0) == 2.0)
  }

  test("Read splitted CSV file with header") {
    val inputPath = makeFileCopy("/test.partitions")
    sparkContext.hadoopConfiguration.setLong(SpatialFileRDD.MaxSplitSize, 1024)
    val data = sparkContext.readWKTFile(inputPath.getPath, "Geometry", '\t', true)
    assert(data.count() == 44)
  }

  test("Read CSV file with header") {
    val inputPath = makeFileCopy("/test.partitions")
    val data = sparkContext.readWKTFile(inputPath.getPath, "Geometry", '\t', true)
    val feature = data.first()
    assert(feature.getAttributeName(0) == "ID")
    assert(feature.getAttributeName(1) == "File Name")
  }
}
