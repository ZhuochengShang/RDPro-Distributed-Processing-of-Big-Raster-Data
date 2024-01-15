package ${groupId};

import edu.school.org.lab.rdpro.common.BeastOptions;
import edu.school.org.lab.rdpro.geolite.IFeature;
import edu.school.org.lab.rdpro.io.CSVFeatureReader;
import edu.school.org.lab.rdpro.io.SpatialReader;
import edu.school.org.rdpro.test.JavaSparkTest;
import org.apache.spark.api.java.JavaRDD;

import java.io.File;

public class beastJavaTest extends JavaSparkTest {
  public void testComputeTwoRoundsPoints() {
    // Make a copy of a file from resources
    File input = makeResourceCopy("/input.txt");

    // Read the file
    JavaRDD<IFeature> polygons = SpatialReader.readInput(javaSparkContext(),
        new BeastOptions().set(CSVFeatureReader.FieldSeparator, ","), input.getPath(), "point");

    // Add your tests here
    assertEquals(2, polygons.count());
  }
}