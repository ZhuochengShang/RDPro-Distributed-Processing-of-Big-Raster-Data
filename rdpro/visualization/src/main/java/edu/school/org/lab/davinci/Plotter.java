/*
 * Copyright 2018 University of California, Riverside
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
package edu.school.org.lab.davinci;

import edu.school.org.lab.rdpro.common.BeastOptions;
import edu.school.org.lab.rdpro.geolite.IFeature;
import edu.school.org.lab.rdpro.util.OperationHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.locationtech.jts.geom.Envelope;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**An abstract interface for a component that draws shapes*/
public abstract class Plotter implements Serializable {
  private static final Log LOG = LogFactory.getLog(Plotter.class);

  public static final Map<String, Class<? extends Plotter>> plotters = loadPlotters();

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Metadata {
    /**
     * A short name for the plotter
     * @return a short name for the plotter to access form the command line.
     */
    String shortname();

    /**
     * A short description for this partitioner
     * @return a brief description of this plotter
     */
    String description();

    /**
     * Extension of the images created using this plotter including the dot, e.g., &quot;.png&quot;
     * @return the extension including the dot
     */
    String imageExtension();
  }

  /**
   * Creates and configures a plotter from the given user options. It first searches for a plotter class defined in
   * the parameter {@link CommonVisualizationHelper#PlotterClassName}. Then, it tries to find a plotter defined by
   * a short user-friendly name in the configuration entry {@link CommonVisualizationHelper#PlotterName}. If neither
   * is found, a null is returned.
   * @param opts the configuration that contains the plotter information
   * @return an instance of Plotter as configured in the given configuration
   */
  public static Plotter createAndConfigurePlotter(BeastOptions opts) {
    // 1- Try a class name
    Class<? extends Plotter> plotterClass = opts.getClass(CommonVisualizationHelper.PlotterClassName, null, Plotter.class);
    if (plotterClass != null)
      return getConfiguredPlotter(plotterClass, opts);
    // 2- Try a short name
    String plotterName = opts.getString(CommonVisualizationHelper.PlotterName);
    if (plotterName != null)
      return getConfiguredPlotter(plotterName, opts);
    // Neither is found
    return null;
  }



  /**
   * Creates and configures a plotter for visualization.
   * @param plotterShortName the short name of the plotter
   * @param conf additional options that might be set by the user for each plotter type
   * @return the created and configured plotter
   */
  public static Plotter getConfiguredPlotter(String plotterShortName, BeastOptions conf) {
    return getConfiguredPlotter(getPlotterClass(plotterShortName), conf);
  }

  public static Plotter getConfiguredPlotter(Class<? extends Plotter> plotterClass, BeastOptions conf) {
    try {
      Plotter plotter = plotterClass.newInstance();
      plotter.setup(conf);
      return plotter;
    } catch (InstantiationException e) {
      throw new RuntimeException("Error creating plotter", e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Error constructing plotter", e);
    }
  }

  /**
   * The extension of the images generated by the given plotter (including the dot)
   * @param plotterName the short name of the plotter
   * @return the extension of the images that this plotter generates
   */
  public static String getImageExtension(String plotterName) {
    Class<? extends Plotter> plotterClass = getPlotterClass(plotterName);
    return plotterClass == null? null : getImageExtension(plotterClass);
  }

  /**
   * The extension of the images created by the given plotter.
   * @param plotterClass the class of the plotter
   * @return the extension of the images that this plotter generates
   */
  public static String getImageExtension(Class<? extends Plotter> plotterClass) {
    Plotter.Metadata metadata = plotterClass.getAnnotation(Plotter.Metadata.class);
    if (metadata == null)
      return null;
    return metadata.imageExtension();
  }

  public static Class<? extends Plotter> getPlotterClass(String plotterName) {
    return plotters.get(plotterName);
  }

  public static Map<String, Class<? extends Plotter>> loadPlotters() {
    Map<String, Class<? extends Plotter>> partitioners = new TreeMap<>();
    List<String> partitionerClasses = OperationHelper.readConfigurationXML("rdpro.xml").get("Plotters");
    for (String partitionerClassName : partitionerClasses) {
      try {
        Class<? extends Plotter> plotterClass =
            Class.forName(partitionerClassName).asSubclass(Plotter.class);
        Plotter.Metadata metadata = plotterClass.getAnnotation(Plotter.Metadata.class);
        if (metadata == null) {
          LOG.warn(String.format("Skipping plotter '%s' without a valid Metadata annotation", plotterClass.getName()));
        } else {
          partitioners.put(metadata.shortname(), plotterClass);
        }
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }
    return partitioners;
  }

  /**
   * Reads and configures a plotter to use in visualization.
   * @param opts any additional parameters that could be set by the user
   */
  public void setup(BeastOptions opts) {
    // An empty body to allow plotters to skip implementing this function if not needed
  }

  /**
   * Returns the buffer size in terms of pixels.
   * @return the size of the buffer in pixels
   */
  public int getBufferSize() {
    return 0;
  }
  
  /**
   * Smooth a set of records that are spatially close to each other and returns
   * a new set of smoothed records. This method is called on the original
   * raw data before it is visualized. The results of this function are the
   * records that are visualized.
   * @param features the list of features to smooth
   * @return an iterator to the output list of features
   */
  public Iterable<? extends IFeature> smooth(Iterable<? extends IFeature> features) {
    return features;
  }

  /**
   * Creates an empty canvas of the given width and height.
   * @param width - Width of the created layer in pixels
   * @param height - Height of the created layer in pixels
   * @param mbr - The minimal bounding rectangle of the layer in the input
   * @param tileID - the ID of this tile for tiled-map visualization or zero for single-level visualization
   * @return the created canvas
   */
  public abstract Canvas createCanvas(int width, int height, Envelope mbr, long tileID);

  /**
   * Plots one shape to the given layer
   * @param layer - the canvas to plot to. This canvas has to be created
   * using the method {@link #createCanvas(int, int, Envelope, long)}.
   * @param feature - the spatial feature to plot
   * @return {@code true} if the canvas was changed as a result of this plot.
   */
  public abstract boolean plot(Canvas layer, IFeature feature);

  /**
   * Merges the second canvas into the first canvas and returns the first canvas
   * @param finalCanvas the final canvas that will contain the result
   * @param intermediateCanvas the intermediate canvas that will be merged into the final canvas
   * @return the final canvas after merging
   */
  public abstract Canvas merge(Canvas finalCanvas, Canvas intermediateCanvas);

  /**
   * Writes a canvas as an image to the output.
   * @param layer - the layer to be written to the output as an image
   * @param out - the output stream to which the image will be written
   * @param vflip - if <code>true</code>, the image is vertically flipped before written
   * @throws IOException if an error happens while writing the output image
   */
  public abstract void writeImage(Canvas layer, OutputStream out, boolean vflip) throws IOException;
  
  /**
   * Returns the raster class associated with this rasterizer
   * @return the class of the canvas. This is helpful when working with Hadoop which requires knowing the class of
   * the intermediate key-value pairs before the job starts.
   */
  public Class<? extends Canvas> getCanvasClass() {
    return this.createCanvas(0, 0, new Envelope(), 0).getClass();
  }

  /**
   * Plots a set of shapes to the given layer
   * @param layer - the canvas to plot to. This canvas has to be created
   * using the method {@link #createCanvas(int, int, Envelope, long)}.
   * @param shapes - a set of shapes to plot
   * @return {@code true} if the canvas was changed as a result of the plot
   */
  public boolean plot(Canvas layer, Iterable<? extends IFeature> shapes) {
    boolean changed = false;
    for (IFeature shape : shapes)
      changed = plot(layer, shape) || changed;
    return changed;
  }
}