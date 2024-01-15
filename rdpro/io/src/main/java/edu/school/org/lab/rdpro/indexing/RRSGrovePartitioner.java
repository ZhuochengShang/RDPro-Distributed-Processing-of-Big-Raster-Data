package edu.school.org.lab.rdpro.indexing;

import edu.school.org.lab.rdpro.cg.SpatialPartitioner;
import edu.school.org.lab.rdpro.geolite.EnvelopeNDLite;
import edu.school.org.lab.rdpro.cg.SpatialPartitioner;
import edu.school.org.lab.rdpro.geolite.EnvelopeNDLite;

/**
 * A spatial partitioner that uses the RR*-Grove partitioning algorithm. Simply, it applies the RR*-tree node splitting
 * method iteratively until each partition contains betweem [m, M] records.
 */
@SpatialPartitioner.Metadata(
    disjointSupported = true,
    extension = "rrsgrove",
    description = "A partitioner that uses the RR*-tree node splitting algorithm on a sample of points to partition the space"
)
public class RRSGrovePartitioner extends RSGrovePartitioner {

  protected EnvelopeNDLite[] partitionPoints(double[][] coords, int max, int min) {
    return RRStarTree.partitionPoints(coords, min, max, true, fractionMinSplitSize, aux);
  }
}
