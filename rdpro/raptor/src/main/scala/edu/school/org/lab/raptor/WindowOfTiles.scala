/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.school.org.lab.raptor

import com.esotericsoftware.kryo.DefaultSerializer
import edu.school.org.lab.rdpro.geolite.{DefaultReadOnlyTile, ITile, RasterMetadata}
import edu.school.org.lab.rdpro.io.tiff.ITiffTile
import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.universe._

import scala.:+
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.reflect.ClassTag.Any

/**
 * A memory tiles that is used for window operations. Each pixel in this tile stores up-to `n` values.
 * Each value is at a specific position. If the value is absent, a corresponding bit is unset for that value.
 * @param tileID the ID of the tile
 * @param metadata the raster metadata
 * @param numValues how many values are stored per pixel
 * @param tiles The actual data is stored in separate tiles. This allows us to store any type of value even if T is an array
 * @tparam T the type of measurement values in tiles
 * @tparam U the type of user defined function
 */
//@DefaultSerializer(classOf[WindowOfTilesSerializer[Any,Any]])
protected class WindowOfTiles[T,U](val tileID: Int, val metadata: RasterMetadata,
                                   numTiles: Int, w: Int,
                                   f:(Array[T], Array[Boolean])=>U)(implicit t: ClassTag[T], u: ClassTag[U])  extends Serializable {

  //var statusFlag = status
  val numTiles_val: Int = numTiles
  //val w_val: Int = w
  val user_function :(Array[T], Array[Boolean])=>U  = f
  var sourceTiles =  Array[ITile[T]]()
  var sourceTileIndex =  Array[Int]()
  var finalTile: ITile[U] = _
  var finalValues: Array[U] = _ // new Array[U](metadata.tileWidth*metadata.tileHeight)
  val numValues: Int = (2 * w + 1) * (2 * w + 1)
 // var values: Array[T] = new Array[T](numValues)
  def addTile(tile: ITile[T]): Unit = {
    //assertion flag is false
    assert(sourceTiles.length <= numTiles)
    sourceTiles +:= tile
    if (sourceTiles.length == numTiles) {
      //statusFlag = true
      //finalTile = getFinalValue()
      println(" call get final value")
      finalTile = getFinalValue()
    }
  }

  def computeFinaleValue(): ITile[U] = {
    if ( sourceTiles.length <= numTiles && finalTile == null ) {
      //finalTile = getFinalValue()
      finalTile = getFinalValue()
    }
    this.finalTile
  }
  def getFinalValue(): ITile[U] = {
    // if less than 9 tiles
    sourceTileIndex = new Array[Int](sourceTiles.length)
    for (tindex <- 0 until sourceTiles.length) {
      sourceTileIndex(tindex) = sourceTiles(tindex).tileID
    }

    // sort sourceTiles by ID
    //sourceTiles.sortBy(_.tileID)
    val x1 = metadata.getTileX1(tileID)
    val x2 = metadata.getTileX2(tileID)
    val y1 = metadata.getTileY1(tileID)
    val y2 = metadata.getTileY2(tileID)
    val finalTile = new MemoryTile[U](tileID,metadata)//sourceTiles(4)
    //finalValues = new Array[U](metadata.rasterWidth*metadata.rasterHeight) // entire tile size
    // apply user defined function
    for (y <- y1 to y2) {
      for ( x <- x1 to x2) {
          // find which tile to read
          val f_value: U = getPixelValue(x, y)
          //val offset = x * (y2 - y1) + y
          val offset = metadata.rasterWidth * (y - metadata.y1) + x
          //finalValues(offset) = f_value
          finalTile.setPixelValue(x, y, f_value)
      }
    }
    finalTile.asInstanceOf[ITile[U]]
    // compute memory tile
   //finalValues
  }

  def getPixelValue(i: Int, j: Int): U = { // check i,j
    val read_i_min = i - w;
    val read_j_min = j - w;
    val read_i_max = i + w;
    val read_j_max = j + w;
    //val t = classTag.asInstanceOf[T]


    val values = new Array[T](numValues)

    //val classSymbol1: Symbol = mirror.classSymbol(values.getClass)
    //println(classSymbol1)
    //val isInteger = classSymbol1 == mirror.classSymbol(typeOf[Integer].getClass)
    //val classSymbo_check = mirror.classSymbol(user_function.getClass.getDeclaredMethods()(0).getParameters()(0).getType)
    //val typedMethod = () => user_function.getClass.getDeclaredMethods()(0).asInstanceOf[Integer]
    //println(typedMethod)
    //println(mirror.classSymbol(Class.forName("java.lang.Integer")))
    //println(classSymbo_check)
    //val className = f.getClass.getDeclaredMethods()(0).getParameters()(0).getType
    //val typeArgs = f.getClass.getDeclaredMethods()(0).getTypeParameters()
    val defined: Array[Boolean] = new Array[Boolean](numValues)
    val x1 = metadata.getTileX1(tileID)
    val x2 = metadata.getTileX2(tileID)
    val y1 = metadata.getTileY1(tileID)
    val y2 = metadata.getTileY2(tileID)

    val f_value : U =
    // read directly from the source tile
    if (read_i_min >= x1 && read_i_max <= x2 && read_j_min >= y1 && read_j_max <= y2) {
      val tileIndex = sourceTileIndex.indexOf(tileID)
      readValues(read_i_min, read_j_min, read_i_max, read_j_max, tileIndex, values, defined)
      f(values, defined)
    }
    else {
      var read_tileID = -1
      var tileIndex: Int = -1
      var valueIndex: Int = 0
      var readBuffer_i: Int = Int.MaxValue
      var readBuffer_j: Int = Int.MaxValue
      // todo: if less than 9 tiles added
      for ( read_j <- read_j_min to read_j_max ) {
        for (read_i <- read_i_min to read_i_max ) {
            if( read_i < metadata.x1 || read_i >= metadata.x2 || read_j < metadata.y1 || read_j >= metadata.y2) {
              defined(valueIndex) = false
            } else {
              var real_i = read_i // actual i can be read from target tiles
              var real_j = read_j
              // if pixel inside the current sourceTile
              if (read_i >= x1 && read_i <= x2 && read_j >= y1 && read_j <= y2) {
                read_tileID = tileID
                tileIndex = sourceTileIndex.indexOf(read_tileID)
                if (tileIndex >= 0)
                  defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i,real_j)//sourceTiles(tileIndex).isDefined(real_i, real_j)
              } else {
                readBuffer_i = math.abs(read_i - i) - 1
                readBuffer_j = math.abs(read_j - j) - 1
                // if pixel location at left top
                if (read_i < x1 && read_j < y1) {
                  read_tileID = tileID - metadata.numTilesX - 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2, metadata.y2) -1) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x2 - readBuffer_i
                      real_j = sourceTiles(tileIndex).y2 - readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j) //sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  }
                  else defined(valueIndex) = false
                }
                if (read_i > x2 && read_j > y2) { // right bottom
                  read_tileID = tileID + metadata.numTilesX + 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x1 + readBuffer_i
                      real_j = sourceTiles(tileIndex).y1 + readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  }
                  else defined(valueIndex) = false
                }
                if (read_i >= x1 && read_i <= x2 && read_j < y1) { // top
                  read_tileID = tileID - metadata.numTilesX
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = read_i
                      real_j = sourceTiles(tileIndex).y2 - readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  } else defined(valueIndex) = false
                }
                if (read_i >= x1 && read_i <= x2 && read_j > y2) { // bottom
                  read_tileID = tileID + metadata.numTilesX
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0){
                      real_i = read_i
                      real_j = sourceTiles(tileIndex).y1 + readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  } else defined(valueIndex) = false
                }
                if (read_j >= y1 && read_j <= y2 && read_i < x1) { // left
                  read_tileID = tileID - 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x2 - readBuffer_i
                      real_j = read_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  } else defined(valueIndex) = false
                }
                if (read_j >= y1 && read_j <= y2 && read_i > x2) { // right
                  read_tileID = tileID + 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x1 + readBuffer_i
                      real_j = read_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j) //sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  } else defined(valueIndex) = false
                }
                if (read_i < x1 && read_j > y2) { // left bottom
                  read_tileID = tileID + metadata.numTilesX - 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2, metadata.y2) - 1) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x2 - readBuffer_i
                      real_j = sourceTiles(tileIndex).y1 + readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j) //sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  }
                  else defined(valueIndex) = false
                }
                if (read_i > x2 && read_j < y1) { // right top
                  read_tileID = tileID - metadata.numTilesX + 1
                  if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2, metadata.y2) - 1) {
                    tileIndex = sourceTileIndex.indexOf(read_tileID)
                    if (tileIndex >= 0) {
                      real_i = sourceTiles(tileIndex).x1 + readBuffer_i
                      real_j = sourceTiles(tileIndex).y2 - readBuffer_j
                      defined(valueIndex) = sourceTiles(tileIndex).isDefined(real_i, real_j) //sourceTiles(tileIndex).isDefined(real_i, real_j)
                    }
                  }
                  else defined(valueIndex) = false
                }
              }
              if (defined(valueIndex) && tileIndex >= 0) {
                values(valueIndex) = sourceTiles(tileIndex).getPixelValue(read_i, read_j)
                //n += 1
                valueIndex += 1
              }
            } // end of if pixel i,j locate inside entire raster
        } // end of inside loop
      } // end of outside loop
      f(values, defined)
    }
    f_value
  }

//  def setValue(i: Int, j: Int, position: Int, value: T): Unit =
//    sourceTiles(position).setPixelValue(i, j, value)

  def getValue(i: Int, j: Int, position: Int): T = sourceTiles(position).getPixelValue(i, j)

  /**
   * Returns `true` if this pixel is empty for all values.
   * @param i the column of the pixel
   * @param j the row of the pixel
   * @return `true` if all values are empty for the given pixel. `false` if at least one value existing
   */
  def isPixelEmpty(i: Int, j: Int): Boolean = {
    for (i <- 0 until numValues)
      if (sourceTiles(i).isDefined(i, j))
        return false
    // All are emtpy, return true
    true
  }


  /**
   * Read all the values at the given pixel position and use them to fill the passed two arrays.
   * @param x the column of the pixel to read
   * @param y the row of the pixel to read
   * @param values the output array that will contain all values
   * @param defined the output array that will contain which values are defined (true)
   * @return the number of valid values that were filled in the array
   */
  def readValues(x_min: Int, y_min: Int, x_max: Int, y_max: Int, tileIndex: Int, values: Array[T], defined: Array[Boolean]): Int = {
    assert(values.length == numValues)
    assert(defined.length == numValues)
    var n: Int = 0
    var i = 0
    for (y <- y_min to y_max; x <-x_min to x_max) {
      if( tileIndex >= 0)
        defined(i) = sourceTiles(tileIndex).isDefined(x, y)
      if (defined(i)) {
        n += 1
        values(i) = sourceTiles(tileIndex).getPixelValue(x, y)
        i += 1
      }
    }
    n
  }


  def readValuesWindow(x: Int, y: Int, read_tileID: Int, valueIndex: Int, defined: Array[Boolean]): Unit = {
    assert(defined.length == numValues)
    //val read_tileID = tileID - metadata.numTilesX
    if (read_tileID >= 0 && read_tileID <= metadata.getTileIDAtPixel(metadata.x2 - 1, metadata.y2 - 1)) {
      val tileIndex = sourceTileIndex.indexOf(read_tileID)
      if (tileIndex >= 0) {
        //real_i = read_i
        //real_j = sourceTiles(tileIndex).y2 - readBuffer_j
        defined(valueIndex) = sourceTiles(tileIndex).isDefined(x, y)
      }else  defined(valueIndex) = false
    }
  }

  /**
   * Merge another window memory tile into this one. Takes any defined values in the other window memory tile
   * and merged it into this one.
   * @param another the other memory tile to merge with
   */
//  def mergeWith(another: MemoryTileWindow[T]): Unit = {
//    assert(another.tileID == this.tileID)
//    for (i <- 0 until numValues; y <- sourceTiles(0).y1 to sourceTiles(0).y2; x <- sourceTiles(0).x1 to sourceTiles(0).x2) {
//      if (another.tiles(i).isDefined(x, y)) {
//        this.sourceTiles(i).setPixelValue(x, y, another.tiles(i).getPixelValue(x, y))
//      }
//    }
//  }

  def merge(another: WindowOfTiles[T,U]): Unit = { // question of merge with finalTile
    assert(another.tileID == this.tileID)
    another.sourceTiles.foreach( t =>
    this.sourceTiles +:= t
    )
    //this.sourceTiles +:= another.sourceTiles
  }
}

object WindowOfTiles {

  def create[T, U](tileID: Int, metadata: RasterMetadata, numTiles: Int, w: Int,
                   f:(Array[T], Array[Boolean])=>U, tiles: Array[ITile[T]], finalValues: Array[U])(implicit t:ClassTag[T],u:ClassTag[U]): WindowOfTiles[T, U] = {
    val tile = new WindowOfTiles[T, U](tileID, metadata, numTiles, w, f)
    tile.sourceTiles = tiles
    tile.finalValues = finalValues
    tile
  }
  def create[T,U](tileID: Int, metadata: RasterMetadata, numTiles: Int, w: Int,
                                      f:(Array[T], Array[Boolean])=>U)(implicit t:ClassTag[T],u:ClassTag[U]): WindowOfTiles[T,U] = {
     new WindowOfTiles[T,U](tileID, metadata, numTiles, w, f)
  }

}

