/*
 * Copyright 2022 University of California, Riverside
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
package edu.school.org.lab.rdpro.io.gpxv2

import edu.school.org.lab.rdpro.io.geojsonv2.GeoJSONScanBuilder
import org.apache.hadoop.fs.FileStatus
import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.read.ScanBuilder
import org.apache.spark.sql.connector.write.{LogicalWriteInfo, WriteBuilder}
import org.apache.spark.sql.execution.datasources.FileFormat
import org.apache.spark.sql.execution.datasources.v2.FileTable
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap

case class GPXTable(name: String,
                    sparkSession: SparkSession,
                    options: CaseInsensitiveStringMap,
                    paths: Seq[String],
                    userSpecifiedSchema: Option[StructType],
                    fallbackFileFormat: Class[_ <: FileFormat])
  extends FileTable(sparkSession, options, paths, userSpecifiedSchema) with Logging {

  override def inferSchema(files: Seq[FileStatus]): Option[StructType] = Option(GPXReader2.schema)

  override def newScanBuilder(options: CaseInsensitiveStringMap): ScanBuilder =
    GPXScanBuilder(sparkSession, paths.toArray, schema, dataSchema, options)

  override def newWriteBuilder(info: LogicalWriteInfo): WriteBuilder = ???

  override def formatName: String = "gpx"
}
