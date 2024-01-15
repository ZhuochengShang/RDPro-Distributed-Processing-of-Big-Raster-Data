package edu.school.org.lab.rdpro.io;

import edu.school.org.lab.rdpro.geolite.EnvelopeND;
import edu.school.org.lab.rdpro.geolite.Feature;
import edu.school.org.lab.rdpro.geolite.GeometryReader;
import edu.school.org.lab.rdpro.geolite.IFeature;
import edu.school.org.lab.rdpro.geolite.EnvelopeND;
import edu.school.org.lab.rdpro.geolite.Feature;
import edu.school.org.lab.rdpro.geolite.GeometryReader;
import edu.school.org.lab.rdpro.geolite.IFeature;
import edu.school.org.rdpro.test.JavaSpatialSparkTest;

public class CSVEnvelopeEncoderTest extends JavaSpatialSparkTest {

  public void testEncode2DEnvelope() {
    IFeature feature = new Feature(new Object[] {
        new EnvelopeND(GeometryReader.DefaultGeometryFactory,2, 1.0, 2.0, 5.0, 3.0), "abc", "def"},
        null);
    CSVEnvelopeEncoder encoder = new CSVEnvelopeEncoder(',', new int[] {1,2,4,5});
    assertArrayEquals(new int[] {-1,0,1,-1,2,3}, encoder.orderedColumns);
    String encoded = encoder.apply(feature, new StringBuilder()).toString();
    assertEquals("abc,1.0,2.0,def,5.0,3.0", encoded);
  }
}