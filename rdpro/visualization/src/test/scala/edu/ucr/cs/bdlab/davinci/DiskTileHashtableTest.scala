/*
 * Copyright 2020 University of California, Riverside
 *
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
package edu.ucr.cs.bdlab.davinci

import org.apache.hadoop.fs.Path
import org.apache.spark.test.ScalaSparkTest
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import java.io.File
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class DiskTileHashtableTest extends FunSuite with ScalaSparkTest {
  test("Write and read") {
    val rand = new Random(0)
    val entries = new Array[(Long, (Long, Int))](100).map( _ => (rand.nextLong().abs, (rand.nextLong().abs, rand.nextInt().abs)))
    val file = new Path(scratchPath, "test")
    val fileSystem = file.getFileSystem(sparkContext.hadoopConfiguration)
    DiskTileHashtable.construct(fileSystem, file, entries)

    // Read it back
    for ((key, expectedValue) <- entries) {
      val actualValue = DiskTileHashtable.getValue(fileSystem, file, key)
      assertResult(expectedValue)(actualValue)
    }
    // Read a value that does not exist
    var numNonExistentKeys = 0
    for (i <- 1 to 100) {
      val randomKey = rand.nextLong().abs
      if (!entries.exists(_._1 == randomKey)) {
        numNonExistentKeys += 1
        assertResult(null)(DiskTileHashtable.getValue(fileSystem, file, randomKey))
      }
    }
    assert(numNonExistentKeys > 0)
  }

  test("Write an empty table") {
    val entries = new Array[(Long, (Long, Int))](0)
    val file = new Path(scratchPath, "test")
    val fileSystem = file.getFileSystem(sparkContext.hadoopConfiguration)
    DiskTileHashtable.construct(fileSystem, file, entries)
    assertResult(false)(new File(file.toString).exists())
  }
}
