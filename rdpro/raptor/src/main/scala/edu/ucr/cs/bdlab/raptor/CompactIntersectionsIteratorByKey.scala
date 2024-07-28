package edu.ucr.cs.bdlab.raptor

import java.sql.Timestamp

/**
 * An iterator that flattens intersections into pixel ranges to be ready for processing with a raster file.
 *
 * @param intersections the list of intersections to flatten
 */
class CompactIntersectionsIteratorByKey(intersections: Iterator[((Timestamp,Long), CompactIntersections)])
  extends Iterator[((Timestamp,Long), PixelRange)] {

  /**The current file tile ID that will be returned when next is called*/
  var currentFileTileID: Long = _
  var currentFileTimeStamp: Timestamp = _

  /**The current intersections from which the next pixel range will be returned*/
  var currentIntersections: CompactIntersections = _

  /**Iterator to the y intersections in the current intersections*/
  var ys: java.util.Iterator[java.lang.Long] = _

  /**Iterator to the x intersections in the current intersections*/
  var xs: java.util.Iterator[java.lang.Long] = _

  /**Iterator to geometry indexes in the current intersections*/
  var geometryIndexes: java.util.Iterator[java.lang.Long] = _

  def moveToNextIntersections: Unit = {
    if (intersections.hasNext) {
      val nextValue = intersections.next()
      currentFileTileID = nextValue._1._2
      currentFileTimeStamp = nextValue._1._1
      currentIntersections = nextValue._2
      assert(!currentIntersections.tileID.isEmpty, "Intersections cannot be empty")
      ys = currentIntersections.ys.iterator()
      xs = currentIntersections.xs.iterator()
      geometryIndexes = currentIntersections.geometryIndexes.iterator()
    }
  }

  // Move tot he first intersections list
  moveToNextIntersections

  override def hasNext: Boolean = xs != null && xs.hasNext

  override def next(): ((Timestamp,Long), PixelRange) = {
    val geometryID = geometryIndexes.next().toInt
    val y = ys.next().toInt
    val x1 = xs.next().toInt
    val x2 = xs.next().toInt
    val returnValue = ((currentFileTimeStamp,currentFileTileID), PixelRange(currentIntersections.featureIDs(geometryID), y, x1, x2))
    if (!xs.hasNext)
      moveToNextIntersections
    returnValue
  }
}
