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
package edu.school.org.lab

import edu.school.org.lab.davinci.VisualizationMixin
import edu.school.org.lab.rdpro.cg.SpatialDataTypesMixin
import edu.school.org.lab.rdpro.indexing.IndexMixin
import edu.school.org.lab.rdpro.io.ReadWriteMixin
import edu.school.org.lab.raptor.RaptorMixin
import org.apache.spark.rdpro.SparkSQLRegistration
import org.apache.spark.sql.SparkSession

/**
 * Contains implicit conversions that simplify the access to spatial functions in rdpro.
 * To use it, add the following command to your Scala program.
 *
 * import edu.school.org.lab.rdpro._
 */
package object rdpro extends ReadWriteMixin
  with SpatialOperationsMixin
  with SpatialDataTypesMixin
  with IndexMixin
  with RaptorMixin
  with VisualizationMixin