package edu.ucr.cs.bdlab.raptor

import edu.ucr.cs.bdlab.beast.cg.SpatialDataTypes.RasterRDD
import edu.ucr.cs.bdlab.beast.geolite.{ITile, RasterMetadata}
import edu.ucr.cs.bdlab.raptor.RaptorMixin.RasterReadMixinFunctions
import org.apache.spark.rdd.RDD
import org.apache.spark.test.ScalaSparkTest
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import java.awt.geom.AffineTransform

@RunWith(classOf[JUnitRunner])
class RasterOperationsFocalTest extends FunSuite with ScalaSparkTest {
  test("retile with the reshape method") {
    // Keep the number of pixels as-is but split a single tile of 100x100 into 100 tiles each with 10x10
    val sourceMetadata = new RasterMetadata(0, 0, 100, 100, 100, 100, 4326,
      new AffineTransform())
    val inputTile: MemoryTile[Short] = new MemoryTile(0, sourceMetadata)
    inputTile.setPixelValue(10, 0, 123)
    inputTile.setPixelValue(0, 1, 125)
    inputTile.setPixelValue(50, 3, 44)
    val inputRaster: RDD[ITile[Short]] = sparkContext.parallelize(Seq(inputTile))

    val targetMetadata = new RasterMetadata(0, 0, 100, 100, 10, 10, 4326,
      new AffineTransform())
    val outputRaster = RasterOperationsFocal.reshapeAverage(inputRaster, targetMetadata)
    assertResult(3)(outputRaster.count())
    val tileID1 = targetMetadata.getTileIDAtPixel(10, 0)
    val tile1 = outputRaster.filter(_.tileID == tileID1).first()
    assertResult(123)(tile1.getPixelValue(10, 0))
    val tileID2 = targetMetadata.getTileIDAtPixel(0, 1)
    val tile2 = outputRaster.filter(_.tileID == tileID2).first()
    assertResult(125)(tile2.getPixelValue(0, 1))
    val tileID3 = targetMetadata.getTileIDAtPixel(50, 3)
    val tile3 = outputRaster.filter(_.tileID == tileID3).first()
    assertResult(44)(tile3.getPixelValue(50, 3))
  }

  test("resampleAverage skip empty pixels") {
    // Reduce a 100x100 to  10x10 raster
    val sourceMetadata = new RasterMetadata(0, 0, 100, 100, 100, 100, 4326,
      new AffineTransform())
    val inputTile: MemoryTile[Short] = new MemoryTile(0, sourceMetadata)
    inputTile.setPixelValue(10, 0, 123)
    inputTile.setPixelValue(0, 1, 125)
    inputTile.setPixelValue(50, 3, 44)
    val inputRaster: RDD[ITile[Short]] = sparkContext.parallelize(Seq(inputTile))

    val targetMetadata = sourceMetadata.rescale(10, 10)
    val outputRaster = RasterOperationsFocal.reshapeAverage(inputRaster, targetMetadata)
    assertResult(1)(outputRaster.count())
    val outputTile = outputRaster.first()

    assertResult(10)(outputTile.tileWidth)
    assertResult(10)(outputTile.tileHeight)

    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2; x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y))
      pixelCount += 1

    assertResult(3)(pixelCount)
    assertResult(123)(outputTile.getPixelValue(1, 0))
    assertResult(125)(outputTile.getPixelValue(0, 0))
    assertResult(44)(outputTile.getPixelValue(5, 0))
  }

  test("resampleAverage computes the average in one tile") {
    // Reduce a 100x100 to  10x10 raster
    val sourceMetadata = new RasterMetadata(0, 0, 100, 100, 100, 100, 4326,
      new AffineTransform())
    val inputTile: MemoryTile[Short] = new MemoryTile(0, sourceMetadata)
    inputTile.setPixelValue(0, 0, 120)
    inputTile.setPixelValue(0, 1, 130)
    inputTile.setPixelValue(1, 0, 134)
    inputTile.setPixelValue(50, 3, 44)
    val inputRaster: RDD[ITile[Short]] = sparkContext.parallelize(Seq(inputTile))

    val targetMetadata = sourceMetadata.rescale(10, 10)
    val outputRaster = RasterOperationsFocal.reshapeAverage(inputRaster, targetMetadata)
    assertResult(1)(outputRaster.count())
    val outputTile = outputRaster.first()

    assertResult(10)(outputTile.tileWidth)
    assertResult(10)(outputTile.tileHeight)

    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2; x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y))
      pixelCount += 1

    assertResult(2)(pixelCount)
    assertResult(128)(outputTile.getPixelValue(0, 0))
    assertResult(44)(outputTile.getPixelValue(5, 0))
  }

  test("resampleNN takes one value from the source") {
    // Reduce a 9x9 raster into a 3x3 raster
    val sourceMetadata = new RasterMetadata(0, 0, 9, 9, 9, 9, 4326,
      new AffineTransform())
    val inputTile: MemoryTile[Short] = new MemoryTile(0, sourceMetadata)
    inputTile.setPixelValue(0, 0, 120)
    inputTile.setPixelValue(0, 1, 130)
    inputTile.setPixelValue(1, 0, 134)
    inputTile.setPixelValue(1, 1, 10)
    inputTile.setPixelValue(4, 4, 44)
    inputTile.setPixelValue(8, 8, 55)
    val inputRaster: RDD[ITile[Short]] = sparkContext.parallelize(Seq(inputTile))

    val outputRaster = RasterOperationsFocal.rescale(inputRaster, 3, 3,
      RasterOperationsFocal.InterpolationMethod.NearestNeighbor)
    assertResult(1)(outputRaster.count())
    val outputTile = outputRaster.first()

    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2; x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y))
      pixelCount += 1

    assertResult(2)(pixelCount)
    assertResult(10)(outputTile.getPixelValue(0, 0))
    assertResult(44)(outputTile.getPixelValue(1, 1))
  }

  test("resample merge intermediate tiles") {
    val metadata = new RasterMetadata(0, 0, 360, 180, 90, 90, 4326,
      new AffineTransform(1, 0, 0, -1, -180, 90))
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (180, 0, 200),
      (100, 50, 300),
    ))
    val raster = RasterOperationsGlobal.rasterizePixels(pixels, metadata)

    val resampledRaster = RasterOperationsFocal.reshapeAverage(raster, metadata.rescale(90, 90))
    assertResult(1)(resampledRaster.count())
    val outputTile = resampledRaster.first()
    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2; x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y))
      pixelCount += 1
    assertResult(3)(pixelCount)
    assertResult(100)(outputTile.getPixelValue(0, 0))
    assert(outputTile.isDefined(180 / 4, 0))
    assertResult(200)(outputTile.getPixelValue(180 / 4, 0))
  }

  test("window smoothing") {
    val metadata = new RasterMetadata(0, 0, 20, 20, 10, 10, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (9, 9, 10),
      (10, 9, 16),
      (10, 10, 18),
      (11, 9, 25),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val smoothedRaster: RasterRDD[Double] = RasterOperationsFocal.slidingWindow(raster, 1, (values: Array[Int], defined) => {
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toDouble / count
    })

    val finalPixels: Map[(Int, Int), Double] = RasterOperationsGlobal.flatten(smoothedRaster)
      .map(x => ((x._1, x._2), x._4))
      .collectAsMap()
      .toMap
    assert(finalPixels.contains((0, 0)))
    assertResult(350 / 3.0)(finalPixels((0, 0)))
    assert(finalPixels.contains((9, 9)))
    assertResult((10 + 16 + 18) / 3.0)(finalPixels((9, 9)))
    assert(finalPixels.contains((10, 10)))
    assertResult((10 + 16 + 18 + 25) / 4.0)(finalPixels((10, 10)))
  }

  test("window smoothing new implement") {
    val metadata = new RasterMetadata(0, 0, 4, 4, 4, 4, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (3, 3, 10),
      (2, 1, 70)
    )) // todo: empty tile
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
//    raster.foreach(tile => {
//      for (y <- tile.y1 to tile.y2) {
//        for (x <- tile.x1 to tile.x2) {
//          print("("+x+","+y+"): "+tile.getPixelValue(x, y))
//          print(" , ")
//        }
//        println("----")
//      }
//      println("----")
//    })
    val smoothedRaster: RasterRDD[Double] = RasterOperationsFocal.slidingWindowImp(raster, 1, (values: Array[Int], defined) => {
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toDouble / count
    })

    val finalPixels: Map[(Int, Int), Double] = RasterOperationsGlobal.flatten(smoothedRaster)
      .map(x => ((x._1, x._2), x._4))
      .collectAsMap()
      .toMap

    assert(finalPixels.contains((0, 0)))
    assertResult(350 / 3.0)(finalPixels((0, 0)))
    assert(finalPixels.contains((3, 3)))
    assertResult((10 ) / 1.0)(finalPixels((3, 3)))
    assert(finalPixels.contains((2, 1)))
    assertResult((70+300+200) / 3.0)(finalPixels((2, 1)))
  }

  test("window new smoothing new implement") {
    val metadata = new RasterMetadata(0, 0, 20, 20, 10, 10, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (9, 9, 10),
      (10, 9, 16),
      (10, 10, 18),
      (11, 9, 25),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val smoothedRaster: RasterRDD[Double] = RasterOperationsFocal.slidingWindowImp(raster, 1, (values: Array[Int], defined) => {
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toDouble / count
    })

    val finalPixels: Map[(Int, Int), Double] = RasterOperationsGlobal.flatten(smoothedRaster)
      .map(x => ((x._1, x._2), x._4))
      .collectAsMap()
      .toMap
    assert(finalPixels.contains((0, 0)))
    assertResult(350 / 3.0)(finalPixels((0, 0)))
    assert(finalPixels.contains((9, 9)))
    assertResult((10 + 16 + 18) / 3.0)(finalPixels((9, 9)))
    assert(finalPixels.contains((10, 10)))
    assertResult((10 + 16 + 18 + 25) / 4.0)(finalPixels((10, 10)))
  }

  test("window multiple smoothing new implement") {
    val metadata = new RasterMetadata(0, 0, 8, 8, 4, 4, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
//      (0, 0, 100),
//      (1, 0, 200),
//      (0, 1, 50),
//      (2, 0, 300),
      (3, 3, 10),
//      (2, 1, 70),
      (4, 2, 5),
      (4, 3, 7),
      (3, 4, 8),
      (4, 4, 25),
//      (2, 5, 15),
//      (6, 2, 30),
//      (6, 3, 10)
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val smoothedRaster: RasterRDD[Double] = RasterOperationsFocal.slidingWindowImp(raster, 1, (values: Array[Int], defined) => {
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toDouble / count
    })

    smoothedRaster.foreach(tile => {
          println(tile.tileID)
          for (y <- tile.y1 to tile.y2) {
            for (x <- tile.x1 to tile.x2) {
              print("("+x+","+y+"): "+tile.getPixelValue(x, y))
              print(" , ")
            }
            println("----")
          }
          println("----")
        })
    val finalPixels: Map[(Int, Int), Double] = RasterOperationsGlobal.flatten(smoothedRaster)
      .map(x => ((x._1, x._2), x._4))
      .collectAsMap()
      .toMap

//    assert(finalPixels.contains((0, 0)))
//    assertResult(350 / 3.0)(finalPixels((0, 0)))
    assert(finalPixels.contains((4, 3)))
    assertResult((5+10+7+8+25) / 5.0)(finalPixels((4, 3)))
    assert(finalPixels.contains((3, 4)))
    assertResult((10 + 7 + 8 + 25) / 4.0)(finalPixels((3, 4)))
//    assert(finalPixels.contains((3, 3)))
//    assertResult((10 + 25 + 5) / 3.0)(finalPixels((3, 3)))
//    assert(finalPixels.contains((2, 1)))
//    assertResult((70 + 300 + 200) / 3.0)(finalPixels((2, 1)))
//    assert(finalPixels.contains((4, 4)))
//    assertResult((10 + 25 ) / 2.0)(finalPixels((4, 4)))
  }

  test("retile with the direct method") {
    val metadata = new RasterMetadata(0, 0, 8, 8, 4, 4, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (3, 3, 10),
      (4, 4, 16),
      (2, 6, 5),
      (5, 3, 25),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    raster.foreach(tile => {
      for (y <- tile.y1 to tile.y2) {
        for (x <- tile.x1 to tile.x2) {
          print(tile.getPixelValue(x, y))
          println(" , ")
        }
        println("----")
      }
      println("----")
    })
    //val retiled: RasterRDD[Int] = RasterOperationsFocal.retile(raster, 20, 20)
    //assertResult(1)(retiled.count())
  }

  test("window smoothing real data") {
    //val file = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/glc2000_small.tif" //"hdfs://localhost:9000/user/data/LC08_L1TP_039036_20211012_20211019_02_T1_refl4326.tif"
    val file = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/20211001_191118_63_240f_3B_Visual.tif"
    val file_out = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/20211001_191118_63_240f_3B_Visual.tif.smooth"
    val rasterRDDFile: RasterRDD[Array[Int]] = sparkContext.geoTiff(file)
    val mapRasterRDDFile: RasterRDD[Int] = RasterOperationsLocal.mapPixels(rasterRDDFile, (x: Array[Int]) => x(0))

    //val rasterRDDFileTiled =  RasterOperationsFocal.retile(rasterRDDFile,256,256)
    val smoothedRaster: RasterRDD[Float] = RasterOperationsFocal.slidingWindowImp(mapRasterRDDFile, 1, (values: Array[Int], defined) => {
      // array : [values[each has 3 bands]]
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toFloat / count
      })
    //val smoothedRaster: RasterRDD[Int] = RasterOperationsFocal.slidingWindowCONV(rasterRDDFileTiled, 1)
    smoothedRaster.foreach(tile => tile.getPixelValue(tile.x1, tile.y1))
    GeoTiffWriter.saveAsGeoTiff(smoothedRaster, file_out, Seq(GeoTiffWriter.WriteMode -> "compatibility"))

  }

  test("window smoothing real data origin") {
    val file = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/glc2000_small.tif" //"hdfs://localhost:9000/user/data/LC08_L1TP_039036_20211012_20211019_02_T1_refl4326.tif"
    //val file = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/20211001_191118_63_240f_3B_Visual.tif"
    val file_out = "/Users/clockorangezoe/Documents/phd_projects/data/Raster/glc2000_small.tif.smooth.origin"
    val raster: RDD[ITile[Int]] = sparkContext.geoTiff(file)

    val smoothedRaster: RasterRDD[Float] = RasterOperationsFocal.slidingWindow(raster, 1, (values: Array[Int], defined) => {
      var sum: Int = 0
      var count: Int = 0
      for (i <- values.indices; if defined(i)) {
        sum += values(i)
        count += 1
      }
      sum.toFloat / count
    })

    GeoTiffWriter.saveAsGeoTiff(smoothedRaster, file_out, Seq(GeoTiffWriter.WriteMode -> "compatibility"))

  }



  test("reshape with a subset") {
    // Use reshape to extract a subset of the data where some input tiles will not produce any target tiles
    val metadata = new RasterMetadata(0, 0, 100, 100, 10, 10, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (10, 0, 200),
      (55, 56, 50),
      (0, 1, 50),
      (99, 99, 25),
      (95, 5, 125),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val reshaped = RasterOperationsFocal.reshapeAverage(raster,
      RasterMetadata.create(90, 0, 100, 10, 4326, 10, 10, 10, 10))
    assertResult(1)(reshaped.count())
  }

  test("rescale combine multiple pixels into one including empty source pixels") {
    val metadata = new RasterMetadata(0, 0, 20, 20, 10, 10, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (9, 9, 10),
      (10, 9, 16),
      (10, 10, 18),
      (11, 9, 25),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val rescaled: RasterRDD[Int] = RasterOperationsFocal.rescale(raster, 4, 4,
      RasterOperationsFocal.InterpolationMethod.Average)
    assertResult(1)(rescaled.count())
    val outputTile = rescaled.first()
    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2;
         x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y)) {
      pixelCount += 1
    }
    assertResult(4)(pixelCount)
  }

  test("reshape with slight shift in the raster") {
    val metadata = new RasterMetadata(0, 0, 10, 10, 10, 10, 4326,
      new AffineTransform())
    val pixels = sparkContext.parallelize(Seq(
      (0, 0, 100),
      (1, 0, 200),
      (0, 1, 50),
      (2, 0, 300),
      (9, 9, 10),
    ))
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val targetMetadata = RasterMetadata.create(0.1, 0.1, 10.1, 10.1, 4326,
      4, 4, 4, 4)
    val rescaled: RasterRDD[Int] = RasterOperationsFocal.reshapeAverage(raster, targetMetadata)
    assertResult(1)(rescaled.count())
    val outputTile = rescaled.first()
    var pixelCount = 0
    for (y <- outputTile.y1 to outputTile.y2;
         x <- outputTile.x1 to outputTile.x2; if outputTile.isDefined(x, y)) {
      pixelCount += 1
    }
    assertResult(2)(pixelCount)
  }

  test("reshape with a target tile slightly outside a source tile") {
    // Create 8x8 tiles to ensure that the bit mask will all be filled with ones (64 bits)
    val metadata = new RasterMetadata(0, 0, 16, 16, 8, 8, 4326,
      new AffineTransform())
    // Fill all the pixels to ensure that isDefined will always return true
    val pixels = sparkContext.parallelize(
      for (x <- 0 until 16; y <- 0 until 16)
        yield (x, y, 10)
    )
    val raster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, metadata)
    val targetMetadata = RasterMetadata.create(-0.1, -0.1, 16-0.1, 16-0.1, 4326,
      4, 4, 2, 2)
    val rescaled: RasterRDD[Int] = RasterOperationsFocal.reshapeAverage(raster, targetMetadata)
    assertResult(4)(rescaled.count())
  }

  test("reshape average with a source tile not covering the center of a target pixel") {
    val sourceMetadata = RasterMetadata.create(6, 6.2, 6.2, 6, 4326, 4, 4, 4, 4)
    val targetMetadata = RasterMetadata.create(0, 16, 16, 0, 4326, 16, 16, 8, 8)
    // Fill all the pixels of the source raster
    val pixels = sparkContext.parallelize(
      for (x <- 0 until 3; y <- 0 until 3)
        yield (x, y, 10)
    )
    val sourceRaster: RasterRDD[Int] = RasterOperationsGlobal.rasterizePixels(pixels, sourceMetadata)
    val rescaled: RasterRDD[Int] = RasterOperationsFocal.reshapeAverage(sourceRaster, targetMetadata)
    // Target should contain one tile
    assertResult(1)(rescaled.count())
  }
}