package edu.school.org.lab.rdpro.operations

import java.io.{File, FileOutputStream, PrintStream}
import edu.school.org.lab.rdpro._
import edu.school.org.lab.rdpro.cg.SpatialJoinAlgorithms.{ESJDistributedAlgorithm, ESJPredicate}
import edu.school.org.lab.rdpro.geolite.{EnvelopeND, EnvelopeNDLite, Feature}
import edu.school.org.lab.rdpro.indexing.GridPartitioner
import org.apache.spark.test.ScalaSparkTest
import org.junit.runner.RunWith
import org.locationtech.jts.geom.GeometryFactory
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SpatialIntersectionRDDTest extends FunSuite with ScalaSparkTest {
  test("Join complex partitioned files") {
    val parksFile = new File(scratchDir, "parks_index")
    makeResourceCopy("/parks_index", parksFile)
    for (i <- 0 to 3) {
      val fileout = new PrintStream(new FileOutputStream("%s/part-%05d.csv".format(parksFile, i)))
      for (j <- 0 to 9)
        fileout.println("EMPTY\t%093d".format(j))
      fileout.close()
    }
    val lakesFile = makeDirCopy("/lakes_index").getPath
    for (i <- 0 to 4) {
      val fileout = new PrintStream(new FileOutputStream("%s/part-%05d.csv".format(lakesFile, i)))
      for (j <- 0 to 9)
        fileout.println("EMPTY\t%093d".format(j))
      fileout.close()
    }
    val parks = sparkContext.spatialFile(parksFile.getPath, "wkt")
    val lakes = sparkContext.spatialFile(lakesFile, "wkt")
    val joined = new SpatialIntersectionRDD1(parks, lakes)
    assert(joined.getNumPartitions == 6)
    assert(joined.count() == 6)
  }
}
