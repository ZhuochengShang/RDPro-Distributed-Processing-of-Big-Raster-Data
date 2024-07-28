package edu.ucr.cs.bdlab.raptor

import edu.ucr.cs.bdlab.beast.cg.Reprojector
import edu.ucr.cs.bdlab.beast.cg.SpatialDataTypes.RasterRDD
import edu.ucr.cs.bdlab.beast.common.BeastOptions
import edu.ucr.cs.bdlab.beast.geolite.{Feature, IFeature, RasterFeature, RasterMetadata}
import edu.ucr.cs.bdlab.beast.io.SpatialReader
import edu.ucr.cs.bdlab.beast.sql.CreationFunctions.geometryFactory
import org.apache.spark.beast.CRSServer
import org.apache.spark.rdd.RDD
import org.apache.spark.test.ScalaSparkTest
import org.geotools.referencing.operation.transform.AffineTransform2D
import org.junit.runner.RunWith
import org.locationtech.jts.geom.{Coordinate, CoordinateSequence, Envelope, GeometryFactory, PrecisionModel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

import java.awt.geom.Point2D

@RunWith(classOf[JUnitRunner])
class RaptorJoinTest extends AnyFunSuite with ScalaSparkTest {

  val factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326)
  def createSequence(points: (Double, Double)*): CoordinateSequence = {
    val cs = factory.getCoordinateSequenceFactory.create(points.length, 2)
    for (i <- points.indices) {
      cs.setOrdinate(i, 0, points(i)._1)
      cs.setOrdinate(i, 1, points(i)._2)
    }
    cs
  }

  test("RaptorJoinZS with RDD") {
    val rasterFile = makeFileCopy("/raptor/glc2000_small.tif").getPath
    val testPoly = factory.toGeometry(new Envelope(-82.76, -80.25, 31.91, 35.17))
    val vector: RDD[(Long, IFeature)] = sparkContext.parallelize(Seq((1L, Feature.create(null, testPoly))))
    val raster: RasterFileRDD[Int] = new RasterFileRDD[Int](sparkContext, rasterFile, new BeastOptions())

    val values: RDD[RaptorJoinResult[Int]] = RaptorJoin.raptorJoinIDFull(raster, vector, new BeastOptions())

    // Values sorted by (x, y)
    val finalValues: Array[RaptorJoinResult[Int]] = values.collect().sortWith((a, b) => a.x < b.x || a.x == b.x && a.y < b.y)
    assert(finalValues.length == 6)
    assert(finalValues(0).x == 69)
    assert(finalValues(0).y == 48)
  }

  test("RaptorJoin with multiband") {
    val rasterFile = makeFileCopy("/rasters/FRClouds.tif").getPath
    val testPoly = factory.toGeometry(new Envelope(0, 3, 45, 47))
    val vector: RDD[(Long, IFeature)] = sparkContext.parallelize(Seq((1L, Feature.create(null, testPoly))))
    val raster: RasterFileRDD[Array[Int]] = new RasterFileRDD[Array[Int]](sparkContext, rasterFile, IRasterReader.OverrideSRID -> 4326)

    val values: RDD[RaptorJoinResult[Array[Int]]] = RaptorJoin.raptorJoinIDFull(raster, vector, new BeastOptions())

    assertResult(classOf[Array[Int]])(values.first().m.getClass)
  }

  test("RaptorJoinZS with RDD and reproject") {
    val rasterFile = makeFileCopy("/rasters/glc2000_small_EPSG3857.tif").getPath
    val testPoly = factory.toGeometry(new Envelope(-109, -106, 36, 40))
    val vector: RDD[(Long, IFeature)] = sparkContext.parallelize(Seq((1L, Feature.create(null, testPoly))))
    val raster: RasterFileRDD[Int] = new RasterFileRDD[Int](sparkContext, rasterFile, new BeastOptions())

    val values: RDD[RaptorJoinResult[Int]] = RaptorJoin.raptorJoinIDFull(raster, vector, new BeastOptions())

    // Values sorted by (x, y)
    val finalValues: Array[RaptorJoinResult[Int]] = values.collect().sortWith((a, b) => a.x < b.x || a.x == b.x && a.y < b.y)
    assert(finalValues.length == 6)
    assert(finalValues(0).m == 12.0f)
  }

  test("EmptyResult with RDD") {
    val rasterFile = makeFileCopy("/raptor/glc2000_small.tif").getPath
    val testPoly = factory.createPolygon()
    val vector: RDD[(Long, IFeature)] = sparkContext.parallelize(Seq((1L, Feature.create(null, testPoly))))
    val raster: RasterFileRDD[Int] = new RasterFileRDD[Int](sparkContext, rasterFile, new BeastOptions())

    val values: RDD[RaptorJoinResult[Int]] = RaptorJoin.raptorJoinIDFull(raster, vector, new BeastOptions())

    // Values sorted by (x, y)
    val finalValues: Array[RaptorJoinResult[Int]] = values.collect().sortWith((a, b) => a.x < b.x || a.x == b.x && a.y < b.y)
    assert(finalValues.isEmpty)
  }

  test("intersection length test") {
    // read in shapefile
    val waterfarm = "/Users/clockorangezoe/Documents/phd_projects/code/samcrop/script.py/farmland/0700714.shp"
    val AOI: RDD[IFeature] = SpatialReader.readInput(sparkContext, new BeastOptions(), waterfarm, "shapefile")
    val waterfarm_vector = AOI.zipWithUniqueId().map(p => (p._2, p._1))
    // read in raster file
    val raster = "/Users/clockorangezoe/Downloads/LABEL_MAPS/T10SEH_2018_RAW_LABEL.tif"
    val rasterRDD: RasterRDD[Int] = new RasterFileRDD[Int](sparkContext, raster, new BeastOptions())
    //val retileRasterRDD = RasterOperationsFocal.retile(rasterRDD, 256, 256)

    val joinres = RaptorJoin.raptorJoinIDFull(rasterRDD, waterfarm_vector, new BeastOptions())
    val pixel: RDD[(Int, Int, Int)] = joinres.map(res => {
      (res.x, res.y, res.m)
    })
    val mask: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixel, rasterRDD.first().rasterMetadata, RasterFeature.create(Array("fileName"), Array("10eh")))
//    mask.foreach(tile => {
//      val list = (tile.pixelLocations)
//      tile.getPixelValue(tile.x1,tile.y1)
//    })
    val out = "/Users/clockorangezoe/Downloads/waterfarm.tif/test.tif"
    //val newMask = RasterOperationsLocal.filterPixels(mask, (x: Int) => (x != null && x != 0 && x == 1))
   // GeoTiffWriter.saveAsGeoTiff(mask, out, Seq(GeoTiffWriter.BitsPerSample -> 8, GeoTiffWriter.WriteMode -> "compatibility"))
  }

  test("Target metadata with AOI") {
    // AOI
    val Yuma = "/Users/clockorangezoe/Desktop/BAITSSS_project/data/yuma/yuma.shp"

    val NLDAS = "/Users/clockorangezoe/Desktop/BAITSSS_project/data/nldas32_4326/NLDAS_FORA0125_H.A20190223.0000.002.tif"
      //"/Users/clockorangezoe/Desktop/BAITSSS_project/data/landsat8/Landsat8_Yuma/ORIGINAL_DATA/LC08_L1TP_038037_20190829_20190903_01_T1_B2.TIF"
    val rasterRDD_NLDAS: RasterRDD[Array[Float]] = new RasterFileRDD[Array[Float]](sparkContext, NLDAS, new BeastOptions())
    val AOI : RDD[IFeature] = SpatialReader.readInput(sparkContext, new BeastOptions(), Yuma, "shapefile")
    val mbr = AOI.first().getGeometry //.getEnvelopeInternal

    val rasterMetadata = rasterRDD_NLDAS.first().rasterMetadata
    val geom = Reprojector.reprojectGeometry(mbr, rasterMetadata.srid)

    val corner1: Point2D.Double = new Point2D.Double
    val corner2: Point2D.Double = new Point2D.Double

    val lineMBR: Envelope = geom.getEnvelopeInternal
    rasterMetadata.modelToGrid(lineMBR.getMinX, lineMBR.getMinY, corner1)
    rasterMetadata.modelToGrid(lineMBR.getMaxX, lineMBR.getMaxY, corner2)

    val i1: Int = Math.max(0, Math.min(corner1.x, corner2.x)).toInt
    val i2: Int = Math.min(rasterMetadata.rasterWidth, Math.ceil(Math.max(corner1.x, corner2.x))).toInt
    val j1: Int = Math.max(0, Math.min(corner1.y, corner2.y)).toInt
    val j2: Int = Math.min(rasterMetadata.rasterHeight, Math.ceil(Math.max(corner1.y, corner2.y))).toInt
//    val corneri1: Point2D.Double = new Point2D.Double
//    val corneri2: Point2D.Double = new Point2D.Double
//    rasterMetadata.gridToModel(i1,j1,corneri1)
//    rasterMetadata.gridToModel(i2,j2,corneri1)

    val result = RaptorJoin.raptorJoinFeature(rasterRDD_NLDAS, AOI)
    result.foreach(res=>{
      println(res.x, res.y)
    })

    val x1 = lineMBR.getMinX
    val x2 = lineMBR.getMaxX
    val y1 = lineMBR.getMinY
    val y2 = lineMBR.getMaxY
    val raterW = Math.floor((x2 - x1) / rasterMetadata.getPixelScaleX)
    val raterH = Math.floor((y2 - y1) / rasterMetadata.getPixelScaleX)

    val targetMetadata = RasterMetadata.create(x1, y2, x2, y1, geom.getSRID, (raterW.toInt), (raterH.toInt), 128, 128)

    val pixelSeq = result.map(pixel => {
      val point: Point2D.Double = new Point2D.Double
      rasterMetadata.gridToModel(pixel.x, pixel.y, point)
      val cord = new Coordinate(point.x,point.y)
      val pointGeom = geometryFactory.createPoint(cord)
      Reprojector.reprojectGeometry(pointGeom,geom.getSRID)
      println(pointGeom.getX,pointGeom.getY)
      (pointGeom.getX, pointGeom.getY, pixel.m)
    })
      println(targetMetadata)
    val landsatRaster = RasterOperationsGlobal.rasterizePoints(pixelSeq, targetMetadata, RasterFeature.create(Array("fileName"),Array("yuma")))

  }
}
