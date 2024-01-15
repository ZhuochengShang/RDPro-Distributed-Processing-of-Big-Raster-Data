/*
 * Copyright 2020 '""
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
package edu.school.org.lab.rdpro.io

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GPXSourceTest extends FunSuite with org.apache.spark.test.beastSpatialTest {
  test("schema of a GPX file") {
    val input = locateResource("/001005279.gpx")
    val dataframe = sparkSession.read.format("gpx").load(input.getPath)
    val schema = dataframe.schema
    assertResult(8)(schema.size)
  }
}
