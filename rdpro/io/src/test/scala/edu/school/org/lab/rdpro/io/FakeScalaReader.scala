package edu.school.org.lab.rdpro.io

import edu.school.org.lab.rdpro.common.BeastOptions
import edu.school.org.lab.rdpro.geolite.IFeature
import edu.school.org.lab.rdpro.common.BeastOptions
import edu.school.org.lab.rdpro.geolite.{EnvelopeND, IFeature}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.{InputSplit, TaskAttemptContext}

@SpatialReaderMetadata(shortName = "fakescalareader", noSplit = true, filter = "*.xyz")
class FakeScalaReader extends FeatureReader {
  override def initialize(inputSplit: InputSplit, conf: BeastOptions): Unit = ???

  override def nextKeyValue(): Boolean = ???

  override def getCurrentValue: IFeature = ???

  override def getProgress: Float = ???

  override def close(): Unit = ???
}