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

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import edu.school.org.lab.rdpro.geolite.{ITile, RasterMetadata}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.{ClassTag, classTag}
class WindowOfTilesSerializer[T,U] extends Serializer[WindowOfTiles[T,U]] {
  override def write(kryo: Kryo, output: Output, tile: WindowOfTiles[T,U]): Unit = {
  // check if final tile is null or not
//    output.writeInt(tile.tileID)
//    kryo.writeObject(output, tile.metadata)
//    output.writeInt(tile.numTiles_val)
//    //output.writeInt(tile.w_val)
//    kryo.writeClassAndObject(output,tile.user_function) // write class and object
//    kryo.writeClassAndObject(output, tile.sourceTiles)
//    kryo.writeClassAndObject(output, tile.finalValues)
  }

  override def read(kryo: Kryo, input: Input, klass: Class[WindowOfTiles[T,U]]): WindowOfTiles[T,U] = {
//    val tileID: Int = input.readInt()
//    val metadata = kryo.readObject(input, classOf[RasterMetadata])
//    val numTiles = input.readInt()
//    val w = input.readInt()
//    val f = kryo.readClassAndObject(input).asInstanceOf[(Array[T], Array[Boolean])=>U]
//    val sourceTiles = kryo.readClassAndObject(input).asInstanceOf[Array[ITile[T]]]
//    val finalValues = kryo.readClassAndObject(input).asInstanceOf[Array[U]]
//    WindowOfTiles.create(tileID,metadata,numTiles,w,f,sourceTiles,finalValues)
    return null
  }
}
