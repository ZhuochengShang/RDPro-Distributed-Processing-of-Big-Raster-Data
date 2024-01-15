package edu.school.org.lab.rdpro.io;

import edu.school.org.rdpro.test.JavaSpatialSparkTest;

public class FeatureReaderTest extends JavaSpatialSparkTest {

  public void testGetFileExtension() {
    String expectedExtension = ".geojson";
    String actualExtension = FeatureReader.getFileExtension("geojson");
    assertEquals(expectedExtension, actualExtension);
  }

}