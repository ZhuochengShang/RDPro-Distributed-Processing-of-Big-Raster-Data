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
package edu.ucr.cs.bdlab.beast.indexing;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.ucr.cs.bdlab.beast.geolite.EmptyGeometry;
import edu.ucr.cs.bdlab.beast.geolite.EnvelopeNDLite;
import edu.ucr.cs.bdlab.beast.geolite.Feature;
import edu.ucr.cs.bdlab.beast.geolite.GeometryHelper;
import edu.ucr.cs.bdlab.beast.geolite.GeometryWriter;
import edu.ucr.cs.bdlab.beast.geolite.IFeature;
import edu.ucr.cs.bdlab.beast.io.FeatureWriter;
import edu.ucr.cs.bdlab.beast.util.BitArray;
import edu.ucr.cs.bdlab.beast.util.CounterOutputStream;
import edu.ucr.cs.bdlab.beast.util.OperationParam;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.beast.CRSServer;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.MapType;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.Map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;

/**
 * Writes features to one file that stores both the record data and an R-tree local index.
 * First, it writes all the features to a temporary file. Then, it constructs the R-tree in memory and flushes it to
 * disk along with the record data.
 * The format of the RTree file is as follows:
 * <ul>
 *   <li>Feature header is written once which includes the names and types of non-geometric attributes</li>
 *   <li>The WKT of the CRS is written once as a String</li>
 *   <li>A list of R-trees is written as pairs of (size, data), where size is the size of the rtree,
 *    and data is the data of the R-tree. This allows splitting the file, if needed, or skipping over R-trees.
 *   </li>
 * </ul>
 * This format works only if all features have a uniform schema, i.e., number of attributes, attribute types, and names.
 */
@FeatureWriter.Metadata(extension = ".rtree", shortName = "rtree")
public class RTreeFeatureWriter extends FeatureWriter {
  private static final Log LOG = LogFactory.getLog(RTreeFeatureWriter.class);

  @OperationParam(
      description = "The type of rtree to build as a local index for each output file {rtree, rstree, rrstree}",
      defaultValue = "rrstree"
  )
  public static final String RTreeTypeConf = "rtreetype";

  /***
   * Maximum size per R-tree in bytes. This ensures that one R-tree will not grow too large to the point that
   * we run out-of-memory while building the tree in memory. Furthermore, one R-tree should not go beyond 2GB to avoid
   * having 64-bit record IDs.
   */
  @OperationParam(
      description = "The maximum size of one R-tree written in the output. If the size exceeds that, multiple R-trees are written",
      defaultValue = "1g"
  )
  public static final String MaxSizePerRTree = "rtree.maxsize";

  /**Final output path to write the R-tree as the writer is closed*/
  protected Path finalOutputPath;

  /**An output stream where the final Rtree will be written*/
  protected DataOutputStream finalOutputStream;

  /**A kryo serializer for writing features temporarily*/
  private Kryo kryo;

  /**Type of tree to build*/
  enum RTreeType {RTree, RSTree, RRSTree};

  /**Type of rtree to build*/
  protected RTreeType rtreeType;

  /**The path to the temporary file for writing the features*/
  protected File tempFile;

  /**A temporary file to write the features until the file is closed before building the index*/
  protected Output tempOut;

  /**A counter stream to estimate the size of the final output*/
  protected CounterOutputStream counter = null;

  /**Number of features written so far. Needed to efficiently build the R-tree*/
  protected int numFeatures;

  /**Number of dimensions for input records*/
  protected int numDimensions;

  /**Hadoop environment configuration*/
  protected Configuration conf;

  /**The spatial reference identifier for all geometries stored in this file*/
  protected int srid;

  /**Threshold of data size per R-tree*/
  protected long maximumSizePerRTree;

  /**A flag that is raised once the header is written to avoid writing it multiple times.*/
  protected boolean headerWritten;

  /**A flag that is raised once the CRS is written to avoid writing it multiple times.*/
  protected boolean crsWritten;

  @Override
  public void initialize(Path p, Configuration conf) throws IOException {
    this.finalOutputPath = p;
    FileSystem fs = p.getFileSystem(conf);
    this.finalOutputStream = fs.create(p);
    initialize(conf);
  }

  @Override
  public void initialize(OutputStream out, Configuration conf) throws IOException {
    this.finalOutputStream = out instanceof DataOutputStream? (DataOutputStream) out : new DataOutputStream(out);
    initialize(conf);
  }

  protected void initialize(Configuration conf) throws IOException {
    this.conf = conf;
    String rtreeTypeStr = conf.get(RTreeTypeConf, "rrstree");
    if (rtreeTypeStr.equalsIgnoreCase("rtree"))
      this.rtreeType = RTreeType.RTree;
    else if (rtreeTypeStr.equalsIgnoreCase("rstree"))
      this.rtreeType = RTreeType.RSTree;
    else if (rtreeTypeStr.equalsIgnoreCase("rrstree"))
      this.rtreeType = RTreeType.RRSTree;
    else
      throw new RuntimeException("Unidentified R-tree type: "+rtreeTypeStr);

    this.maximumSizePerRTree = conf.getLongBytes(MaxSizePerRTree, 1024 * 1024 * 1024);
    headerWritten = false;
    createNewTempFile();
  }

  /**
   * Creates a new temporary file for buffering the features before creating the R-tree. It also resets the
   * counters numFeatures and the data size.
   * @throws IOException if an error happens while creating the temporary file
   */
  protected void createNewTempFile() throws IOException {
    tempFile = File.createTempFile(String.format("%06d", (int)(Math.random() * 1000000)), "rtree");
    // Mark file to delete on exit just in case the process fails without explicitly deleting it
    tempFile.deleteOnExit();
    this.kryo = new Kryo();
    this.kryo.register(Feature.class);
    tempOut = new Output(new BufferedOutputStream(counter = new CounterOutputStream(new FileOutputStream(tempFile))));
    numFeatures = 0;
  }

  @Override
  public void write(IFeature value) throws IOException {
    if (!headerWritten) {
      // First record, write the header once to the final output stream
      writeFeatureHeader(value, finalOutputStream);
      headerWritten = true;
    }
    if (!crsWritten && !value.getGeometry().isEmpty()) {
      // Write CRS. We do not use empty geometries because their SRID might be invalid
      srid = value.getGeometry().getSRID();
      finalOutputStream.writeUTF(srid == 0 ? "" : CRSServer.sridToCRS(srid).toWKT());
      crsWritten = true;
    }
    // Copy number of dimensions from the first geometry that has more than zero dimensions
    // This solves a problem when the first few records have zero dimensions (empty) while remaining records
    // have non-zero dimensions (non-empty)
    if (numDimensions == 0)
      numDimensions = GeometryHelper.getCoordinateDimension(value.getGeometry());
    kryo.writeClassAndObject(tempOut, value);
    if (value.getGeometry() != null && value.getGeometry().getSRID() != srid)
      LOG.warn(String.format("Found mismatching SRID in geometries %d != %d", value.getGeometry().getSRID(), srid));
    numFeatures++;
    if (counter.getCount() + numFeatures * 44 > maximumSizePerRTree) {
      // Accumulated enough data to write an R-tree. Write it out and create a new temporary file
      flushRecords();
      createNewTempFile();
    }
  }

  /**
   * Flush the records that are currently in the buffer, i.e., temp file, to the final output as one R-tree.
   * This method is synchronous; it will block until the R-tree was written and the temproary file is deleted.
   * @throws IOException if an error happens while writing records to disk
   */
  protected void flushRecords() throws IOException {
    assert tempFile != null : "Should not flush records when tempFile is null, i.e., estimating size";
    // Close the temporary file and build the index
    tempOut.close();

    try {
      // Create a new feature to scan over the features in the temporary file
      int[] recordOffsets = new int[numFeatures + 1];
      double[][] minCoord = new double[numDimensions][numFeatures];
      double[][] maxCoord = new double[numDimensions][numFeatures];
      EnvelopeNDLite mbr = new EnvelopeNDLite(numDimensions);

      long biggestFeatureSize = 0;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      try (Input tempIn = new Input(new BufferedInputStream(new FileInputStream(tempFile)))) {
        for (int $i = 0; $i < numFeatures; $i++) {
          recordOffsets[$i] = baos.size();
          IFeature f = (IFeature) kryo.readClassAndObject(tempIn);
          mbr.setEmpty();
          mbr.merge(f.getGeometry());
          for (int d$ = 0; d$ < numDimensions; d$++) {
            minCoord[d$][$i] = mbr.getMinCoord(d$);
            maxCoord[d$][$i] = mbr.getMaxCoord(d$);
          }
          writeFeatureValue(f, dos, false);
          dos.flush();
          int size = baos.size() - recordOffsets[$i];
          if (size > biggestFeatureSize)
            biggestFeatureSize = size;
        }
      }
      dos.close();
      recordOffsets[numFeatures] = baos.size();

      byte[] serializedFeatures = baos.toByteArray();
      // Clear up the non-used writers to give the garbage collector a chance to clear up their resources
      dos = null; baos = null;

      // Now build the R-tree
      RTreeGuttman rtree;
      int M = 100;
      int m;
      switch (this.rtreeType) {
        case RTree:
          m = M / 2;
          rtree = new RTreeGuttman(m, M);
          break;
        case RSTree:
          m = M * 3 / 10;
          rtree = new RStarTree(m, M);
          break;
        case RRSTree:
          m = M * 2 / 10;
          rtree = new RRStarTree(m, M);
          break;
        default:
          throw new RuntimeException("Unsupported rtree type: " + this.rtreeType);
      }
      long t1 = System.nanoTime();
      rtree.initializeFromBoxes(minCoord, maxCoord);
      long t2 = System.nanoTime();
      LOG.info(String.format("Built an in-memory R-tree with %d records in %f seconds", numFeatures, (t2 - t1) * 1E-9));

      byte[] buffer = new byte[(int) biggestFeatureSize];

      // Then, write the entire tree
      rtree.write(finalOutputStream, (out1, iObject) -> {
        int recordSize = recordOffsets[iObject + 1] - recordOffsets[iObject];
        out1.write(serializedFeatures, recordOffsets[iObject], recordSize);
        return recordSize;
      });
      long t3 = System.nanoTime();
      LOG.info(String.format("R-tree with %d records written to disk in %f seconds", numFeatures, (t3 - t2) * 1E-9));
    } finally {
      tempFile.delete();
    }
  }

  @Override
  public void close() throws IOException {
    // Flush the records only if some have been written and a temporary file exists, i.e., not estimating the size
    if (numFeatures > 0 && tempFile != null)
      flushRecords();

    finalOutputStream.close();
  }

  @Override
  public int estimateSize(IFeature value) {
    try {
      if (counter == null) {
        if (tempOut != null) {
          tempOut.close();
          tempOut = null;
        }
        finalOutputStream = new DataOutputStream(counter = new CounterOutputStream());
        tempFile = null;
      }
      long sizeBefore = counter.getCount();
      if (numFeatures == 0) {
        // First record, write the header once
        writeFeatureHeader(value, finalOutputStream);
        numDimensions = GeometryHelper.getCoordinateDimension(value.getGeometry());
      }
      // Negative counter indicates non-existent records
      numFeatures--;
      writeFeatureValue(value, finalOutputStream, false);
      // Add 44 bytes as a rough estimate for the R-tree index overhead (empirically obtained from actual indexes)
      return (int) (counter.getCount() - sizeBefore) + 44;
    } catch (IOException e) {
      e.printStackTrace();
      return 0;
    }
  }

  /**
   * Write the header of the feature to avoid repeating the header for each feature.
   * @param feature the feature to write its header (attribute names and types)
   * @param out the output to write to
   */
  protected static void writeFeatureHeader(IFeature feature, DataOutput out) throws IOException {
    // Write number of attributes (maximum 127 attributes in a byte)
    out.writeByte(feature.length() - 1);
    if (feature.length() > 1) {
      // Write attribute types
      for (int i : feature.iNonGeomJ()) {
        DataType type = feature.getDataType(i);
        writeType(type, out);
      }
      // Write attribute names
      for (int i : feature.iNonGeomJ())
        out.writeUTF(feature.getName(i) == null ? "attr#" + i : feature.getName(i));
    }
  }

  protected static void writeType(DataType dataType, DataOutput out) throws IOException {
    if (dataType == DataTypes.StringType) out.writeByte(RTreeFeatureReader.STRING_TYPE);
    else if (dataType == DataTypes.IntegerType) out.writeByte(RTreeFeatureReader.INTEGER_TYPE);
    else if (dataType == DataTypes.LongType) out.writeByte(RTreeFeatureReader.LONG_TYPE);
    else if (dataType == DataTypes.DoubleType) out.writeByte(RTreeFeatureReader.DOUBLE_TYPE);
    else if (dataType == DataTypes.TimestampType) out.writeByte(RTreeFeatureReader.TIMESTAMP_TYPE);
    else if (dataType == DataTypes.BooleanType) out.writeByte(RTreeFeatureReader.BOOLEAN_TYPE);
    else if (dataType instanceof MapType) {
      out.writeByte(RTreeFeatureReader.MAP_TYPE);
      writeType(((MapType)dataType).keyType(), out);
      writeType(((MapType)dataType).valueType(), out);
    }
    else throw new RuntimeException("Unsupported data type "+ dataType);
  }

  /**
   * Write the values of the given feature to the given output. It does not write the schema.
   * @param feature the feature to write its values
   * @param out the output to write to
   * @param includeSRID whether to write the SRID to the output or not
   * @throws IOException if an error happens while writing the output.
   */
  protected static void writeFeatureValue(IFeature feature, DataOutput out, boolean includeSRID) throws IOException {
    if (feature.length() > 1) {
      BitArray attributeExists = new BitArray(feature.length() - 1);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dataout = new DataOutputStream(baos);
      for (int i : feature.iNonGeomJ()) {
        Object value = feature.get(i);
        if (value != null) {
          attributeExists.set(i < feature.iGeom()? i : i - 1, true);
          DataType dataType = feature.getDataType(i);
          writeValue(dataout, dataType, value);
        }
      }
      dataout.close();
      byte[] bytes = baos.toByteArray();
      out.writeInt(bytes.length);
      out.write(bytes);
      attributeExists.writeBitsMinimal(out);
    }

    new GeometryWriter().write(feature.getGeometry() == null? EmptyGeometry.instance : feature.getGeometry(), out, includeSRID);
  }

  protected static void writeValue(DataOutput dataout, DataType dataType, Object value) throws IOException {
    if (dataType == DataTypes.StringType) {
      byte[] strBytes = ((String) value).getBytes();
      dataout.writeShort(strBytes.length);
      dataout.write(strBytes);
    } else if (dataType == DataTypes.IntegerType) {
      dataout.writeInt(((Number) value).intValue());
    } else if (dataType == DataTypes.LongType) {
      dataout.writeLong(((Number) value).longValue());
    } else if (dataType == DataTypes.DoubleType) {
      dataout.writeDouble(((Number) value).doubleValue());
    } else if (dataType == DataTypes.TimestampType) {
      // Get calendar in UTC
      ZonedDateTime utctime = ZonedDateTime.ofInstant(((GregorianCalendar) value).toZonedDateTime().toInstant(), ZoneOffset.ofTotalSeconds(0));
      value = GregorianCalendar.from(utctime);
      dataout.writeLong(((GregorianCalendar) value).getTimeInMillis());
    } else if (dataType == DataTypes.BooleanType) {
      dataout.writeByte((byte) ((Boolean) value ? 1 : 0));
    } else if (dataType instanceof MapType) {
      Map map = (Map) value;
      dataout.writeInt(map.size());
      Iterator<Tuple2<Object, Object>> iter = map.iterator();
      while (iter.hasNext()) {
        Tuple2<Object, Object> entry = iter.next();
        writeValue(dataout, ((MapType)dataType).keyType(), entry._1);
        writeValue(dataout, ((MapType)dataType).valueType(), entry._2);
      }
    } else {
      throw new RuntimeException("Unsupported type " + dataType);
    }
  }
}
