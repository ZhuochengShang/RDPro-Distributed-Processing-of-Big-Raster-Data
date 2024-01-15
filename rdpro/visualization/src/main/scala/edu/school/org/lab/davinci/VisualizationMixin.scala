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
package edu.school.org.lab.davinci

import edu.school.org.lab.rdpro.cg.CGOperationsMixin._
import edu.school.org.lab.rdpro.cg.SpatialDataTypes.SpatialRDD
import edu.school.org.lab.rdpro.common.BeastOptions
import edu.school.org.lab.rdpro.synopses.Summary
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.rdd.RDD


/**
 * A mixin to add visualization options to [[SpatialRDD]]
 */
trait VisualizationMixin {

  implicit class VisualizationMixinFunctions(features: SpatialRDD) {

    /**
     * Plots the features to an image using the given plotter
     * @param imageWidth the width of the image in pixels
     * @param imageHeight the height of the image in pixels
     * @param imagePath the path to write the generated image
     * @param plotterClass the plotter class
     * @param opts additional user options
     */
    def plotImage(imageWidth: Int, imageHeight: Int, imagePath: String,
                  plotterClass: Class[_ <: Plotter] = classOf[GeometricPlotter],
                  opts: BeastOptions = new BeastOptions()): Unit =
      SingleLevelPlot.plotFeatures(features, imageWidth, imageHeight, imagePath, plotterClass, features.summary, opts)

    /**
     * Plots the dataset as multilevel tiled image and write the output to the given path.
     * @param outPath the output path to write the image tiles to.
     * @param numLevels the number of levels to create
     * @param plotterClass the plotter class to use for plotting
     * @param opts additional options for the plotter
     */
    def plotPyramid(outPath: String, numLevels: Int,
                    plotterClass: Class[_ <: Plotter] = classOf[GeometricPlotter],
                    opts: BeastOptions = new BeastOptions()): Unit = {
      // Set the threshold to zero to generate all tiles
      if (!opts.contains(MultilevelPlot.ImageTileThreshold))
        opts.set(MultilevelPlot.ImageTileThreshold, 0)
      MultilevelPlot.plotFeatures(features, 0 until numLevels, plotterClass, null, outPath, opts)
    }
  }
}

object VisualizationMixin extends VisualizationMixin