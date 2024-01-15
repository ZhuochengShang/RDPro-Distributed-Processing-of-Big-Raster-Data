package edu.school.org.lab.rdpro.io

import edu.school.org.lab.rdpro.indexing.{RTreeFeatureReader, RTreeFeatureWriter}

class RTreeSource extends SpatialFileSourceSink[RTreeFeatureReader, RTreeFeatureWriter] {

  /**A short name to use by users to access this source */
  override def shortName(): String = "rtree"

}
