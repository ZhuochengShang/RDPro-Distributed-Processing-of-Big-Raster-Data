package edu.ucr.cs.bdlab.raptor

import edu.ucr.cs.bdlab.beast.cg.SpatialDataTypes.RasterRDD
import edu.ucr.cs.bdlab.beast.geolite.{ITile, RasterFeature}

import scala.reflect.ClassTag

object RasterFeatureHelper {

  def appendNewFeature[T: ClassTag, U: ClassTag](inputRaster: ITile[T], name: String, value: Any): ITile[T] = {
      new AppendTile[T](inputRaster, name, value)
  }

//  def updateFeature[T: ClassTag, U: ClassTag](name: String, value: Any): RasterRDD[T] = {
//
//  }
}
