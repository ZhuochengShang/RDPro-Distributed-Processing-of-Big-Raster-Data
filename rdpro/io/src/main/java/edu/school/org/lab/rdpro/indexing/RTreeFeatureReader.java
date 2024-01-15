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
package edu.school.org.lab.rdpro.indexing;

import edu.school.org.lab.rdpro.geolite.Feature;
import edu.school.org.lab.rdpro.geolite.GeometryHelper;
import edu.school.org.lab.rdpro.geolite.GeometryReader;
import edu.school.org.lab.rdpro.geolite.IFeature;
import edu.school.org.lab.rdpro.io.SpatialFileRDD;
import edu.school.org.lab.rdpro.common.BeastOptions;
import edu.school.org.lab.rdpro.io.FeatureReader;
import edu.school.org.lab.rdpro.io.SpatialReaderMetadata;
import edu.school.org.lab.rdpro.util.BitArray;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.spark.rdpro.CRSServer;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import scala.Tuple2;

import java.io.Closeable;
import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.GregorianCalendar;
import java.util.Iterator;

/**
 * Reads features from an R-tree-indexed file.
 */
@SpatialReaderMetadata(
    description = "An R-tree locally indexed file for efficient range retrieval",
    shortName = "rtree",
    extension = ".rtree",
    noSplit = true
)
public class RTreeFeatureReader extends FeatureReader {

  static final int STRING_TYPE = 0;
  static final int INTEGER_TYPE = 1;
  static final int LONG_TYPE = 2;
  static final int DOUBLE_TYPE = 3;
  static final int TIMESTAMP_TYPE = 4;
  static final int BOOLEAN_TYPE = 5;
  static final int MAP_TYPE = 6;

  /**The value that is returned*/
  protected IFeature value;

  protected Iterator<? extends IFeature> results;

  /**The file name to report in error messages*/
  private String filename;

  /**The geometry reader to read geometries from the R-tree. Configured to use the right SRID.*/
  private GeometryReader reader;

  /**The input to the file*/
  private FSDataInputStream in;

  /**The start position of the current tree*/
  private long posCurrentTree;

  /**The position of the start tree*/
  private long posFirstTree;

  /**The deserializer reads records from the R-tree*/
  private RTreeGuttman.Deserializer<Feature> featureDeserializer;

  /**If the input should be filtered, these are the search coordinates*/
  private double[] minCoord;
  private double[] maxCoord;

  @Override
  public void initialize(InputSplit split, BeastOptions conf) throws IOException {
    FileSplit fsplit = (FileSplit) split;

    // Open the input file and read the header of the stored features
    filename = fsplit.getPath().toString();
    FileSystem fs = fsplit.getPath().getFileSystem(conf.loadIntoHadoopConf(null));
    in = fs.open(fsplit.getPath());
    in.seek(fsplit.getStart());
    Tuple2<DataType[], String[]> typesNames = readFeatureSchema(in);
    // Data types of all fields including geometry attribute. Used since accessing StructType in Java is not easy
    DataType[] types = typesNames._1;
    String[] names = typesNames._2;
    StructField[] fields = new StructField[types.length];
    for (int i = 0; i < fields.length; i++)
      fields[i] = new StructField(names[i], types[i], true, null);
    StructType schema = new StructType(fields);

    String wkt = in.readUTF();
    int srid;
    if (wkt.isEmpty())
      srid = 0;
    else {
      try {
        CoordinateReferenceSystem crs = CRS.parseWKT(wkt);
        srid = CRSServer.crsToSRID(crs);
      } catch (FactoryException e) {
        srid = 4326;
      }
    }
    reader = GeometryReader.getGeometryReader(srid);

    // The current position is where the reading should stop (starting from the end)
    posFirstTree = in.getPos();
    posCurrentTree = fsplit.getStart() + fsplit.getLength();

    // Now, either read the entire file, or filter based on the MBR
    String filterMBRStr = conf.getString(SpatialFileRDD.FilterMBR());
    if (filterMBRStr != null) {
      // Filter based on the MBR
      String[] parts = filterMBRStr.split(",");
      assert parts.length % 2 == 0; // It has to be an even number
      int numDimensions = parts.length / 2;
      minCoord = new double[numDimensions];
      maxCoord = new double[numDimensions];
      for (int d$ = 0; d$ < numDimensions; d$++) {
        minCoord[d$] = Double.parseDouble(parts[d$]);
        maxCoord[d$] = Double.parseDouble(parts[numDimensions + d$]);
      }
    }
    // Create the deserializer of geometries
    featureDeserializer = input -> {
      try {
        return readFeatureValue(input, types, schema, reader);
      } catch (Exception e) {
        throw new RuntimeException("Error reading feature from file "+filename, e);
      }
    };
   readPreviousRTree();
  }

  /**
   * Read the previous R-tree. The file is read form the end to the beginning.
   */
  private void readPreviousRTree() throws IOException {
    assert posCurrentTree > posFirstTree :
        String.format("Cannot seek before tree at position %d while the start is at %d", posCurrentTree, posFirstTree);
    // Get the tree length by subtracting the Feature header size
    in.seek(posCurrentTree - 4);
    int treeLength = in.readInt() + 4;
    posCurrentTree -= treeLength;
    in.seek(posCurrentTree);

    if (minCoord != null) {
      // Search using the given rectangle
      results = RTreeGuttman.search(in, treeLength, minCoord, maxCoord, featureDeserializer).iterator();
    } else {
      // Read all records
      results = RTreeGuttman.readAll(in, treeLength, featureDeserializer).iterator();
    }
  }

  @Override
  public boolean nextKeyValue() {
    while (results.hasNext() || posCurrentTree > posFirstTree) {
      if (results.hasNext()) {
        value = results.next();
        return true;
      }
      try {
        readPreviousRTree();
      } catch (IOException e) {
        throw new RuntimeException("Error reading R-tree", e);
      }
    }
    return false;
  }

  @Override
  public IFeature getCurrentValue() {
    return value;
  }

  @Override
  public float getProgress() throws IOException {
    return results instanceof RTreeGuttman.DiskSearchIterator?
        ((RTreeGuttman.DiskSearchIterator<IFeature>) results).getProgress() : 0.1f;
  }

  @Override
  public void close() throws IOException {
    if (results != null)
      ((Closeable)results).close();
  }

  /**
   * Reads and returns the header from the given input stream as a list of data types and names.
   * @param in the input stream to read from
   * @return a list of types and names
   * @throws IOException
   */
  protected static Tuple2<DataType[], String[]> readFeatureSchema(DataInput in) throws IOException {
    // Number of attributes = 1 (geometry) + number of non-spatial attributes
    int numAttributes = 1 + in.readUnsignedByte();
    DataType[] types = new DataType[numAttributes];
    types[0] = GeometryHelper.GeometryType;
    String[] names = new String[numAttributes];
    names[0] = "g";
    if (numAttributes > 1) {
      for (int i = 1; i < numAttributes; i++)
        types[i] = readDataType(in);

      for (int i = 1; i < numAttributes; i++)
        names[i] = in.readUTF();
    }
    return new Tuple2(types, names);
  }

  /**
   * Read the SparkSQL data type from the input stream
   * @param in the input stream to read from
   * @return the parsed data type
   * @throws IOException if an error happens while reading from the given stream.
   */
  protected static DataType readDataType(DataInput in) throws IOException {
    int type = in.readByte();
    switch (type) {
      case STRING_TYPE: return DataTypes.StringType;
      case INTEGER_TYPE: return DataTypes.IntegerType;
      case LONG_TYPE: return DataTypes.LongType;
      case DOUBLE_TYPE: return DataTypes.DoubleType;
      case TIMESTAMP_TYPE: return DataTypes.TimestampType;
      case BOOLEAN_TYPE: return DataTypes.BooleanType;
      case MAP_TYPE: {
        // Map type, read key and value types
        DataType keyType = readDataType(in);
        DataType valueType = readDataType(in);
        return DataTypes.createMapType(keyType, valueType);
      }
      default: throw new RuntimeException("Unrecognized type "+type);
    }
  }

  /**
   * Read the geometry and attribute values from the given input and create a new feature
   * @param in the input reader to read the data from
   * @param types the list of types for easy access from Java since StructType is not easily accessible
   * @param schema the schema of the records including the geoemtry attributes
   * @param reader the reader that creates the geometry
   * @return the new feature that was read
   * @throws IOException if an error happens while reading the feature.
   */
  protected static Feature readFeatureValue(DataInput in, DataType[] types, StructType schema,
                                            GeometryReader reader) throws IOException {
    int numNonSpatialAttributes = schema.size() - 1;
    Object[] values = new Object[schema.size()];
    // Read all attributes (except the geometry)
    if (numNonSpatialAttributes > 0) {
      // Read attribute values and which ones are null
      int valueSize = in.readInt();
      byte[] valueBytes = new byte[valueSize];
      in.readFully(valueBytes);
      BitArray attributeExists = new BitArray(numNonSpatialAttributes);
      attributeExists.readBitsMinimal(in);

      // Parse the attribute value
      ByteBuffer buffer = ByteBuffer.wrap(valueBytes);
      for (int i = 1; i < schema.size(); i++) {
        if (attributeExists.get(i - 1)) {
          DataType t = types[i];
          Object value = readValue(buffer, t);
          values[i] = value;
        }
      }
    }
    // Read the geometry
    values[0] = reader.parse(in);
    return new Feature(values, schema);
  }

  protected static Object readValue(ByteBuffer buffer, DataType t) throws IOException {
    Object value;
    if (t == DataTypes.StringType) {
      int stringLength = buffer.getShort();
      value = new String(buffer.array(), buffer.position(), stringLength);
      // Advance the buffer position to skip over the string
      buffer.position(buffer.position() + stringLength);
    } else if (t == DataTypes.IntegerType) {
      value = buffer.getInt();
    } else if (t == DataTypes.LongType) {
      value = buffer.getLong();
    } else if (t == DataTypes.DoubleType) {
      value = buffer.getDouble();
    } else if (t == DataTypes.TimestampType) {
      GregorianCalendar c = new GregorianCalendar(Feature.UTC());
      c.setTimeInMillis(buffer.getLong());
      value = c;
    } else if (t == DataTypes.BooleanType) {
      value = buffer.get() == 1;
    } else if (t instanceof MapType) {
      int size = buffer.getInt();
      scala.collection.Map map = new scala.collection.immutable.HashMap<>();
      for (int i = 0; i < size; i++) {
        Object entryKey = readValue(buffer, ((MapType)t).keyType());
        Object entryValue = readValue(buffer, ((MapType)t).valueType());
        map = map.$plus(new Tuple2<>(entryKey, entryValue));
      }
      value = map;
    } else {
      throw new RuntimeException("Unsupported type " + t);
    }
    return value;
  }
}
