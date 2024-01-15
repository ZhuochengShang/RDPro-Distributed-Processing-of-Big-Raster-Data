/*
 * Copyright (c) 2015 by Regents of the University of Minnesota.
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

package edu.school.org.lab.rdpro;

import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Header of VData
 * @author Ahmed Eldawy
 *
 */
public class DDVDataHeader extends DataDescriptor {

  /** Field indicating interlace scheme used */
  protected int interlace;
  /** Number of entries */
  protected int nvert;
  /** Size of one Vdata entry */
  protected int ivsize;
  /** Field indicating the data type of the nth field of the Vdata*/
  protected int[] types;
  /** Size in bytes of the nth field of the Vdata*/
  protected int[] sizes;
  /** Offset of the nth field within the Vdata (offset in file)*/
  protected int[] offsets;
  /** Order of the nth field of the Vdata*/
  protected int[] order;
  /** Names of the fields */
  protected String[] fieldNames;
  /** Name */
  protected String name;
  /** Class */
  protected String klass;
  /** Extension tag */
  protected int extag;
  /** Extension reference number */
  protected int exref;
  /** Version number of DFTAG_VH information */
  protected int version;

  DDVDataHeader(HDFFile hdfFile, int tagID, int refNo, int offset,
      int length, boolean extended) {
    super(hdfFile, tagID, refNo, offset, length, extended);
  }

  @Override
  protected void readFields(DataInput input) throws IOException {
    this.interlace = input.readUnsignedShort();
    this.nvert = input.readInt();
    this.ivsize = input.readUnsignedShort();
    int nfields = input.readUnsignedShort();
    this.types = new int[nfields];
    for (int i = 0; i < nfields; i++)
      this.types[i] = input.readUnsignedShort();
    this.sizes = new int[nfields];
    for (int i = 0; i < nfields; i++)
      this.sizes[i] = input.readUnsignedShort();
    this.offsets = new int[nfields];
    for (int i = 0; i < nfields; i++)
      this.offsets[i] = input.readUnsignedShort();
    this.order = new int[nfields];
    for (int i = 0; i < nfields; i++)
      this.order[i] = input.readUnsignedShort();
    fieldNames = new String[nfields];
    byte[] nameBytes = new byte[1024];
    for (int i = 0; i < nfields; i++) {
      int fieldNameLength = input.readUnsignedShort();
      if (fieldNameLength > nameBytes.length)
        nameBytes = new byte[fieldNameLength];
      input.readFully(nameBytes, 0, fieldNameLength);
      fieldNames[i] = new String(nameBytes, 0, fieldNameLength);
    }
    int nameLength = input.readUnsignedShort();
    if (nameLength > nameBytes.length)
      nameBytes = new byte[nameLength];
    input.readFully(nameBytes, 0, nameLength);
    name = new String(nameBytes, 0, nameLength);
    
    int classLength = input.readUnsignedShort();
    if (classLength > nameBytes.length)
      nameBytes = new byte[classLength];
    input.readFully(nameBytes, 0, classLength);
    klass = new String(nameBytes, 0, classLength);

    this.extag = input.readUnsignedShort();
    this.exref = input.readUnsignedShort();
    this.version = input.readUnsignedShort();
  }
  
  public int getEntryCount() throws IOException {
    lazyLoad();
    return nvert;
  }
  
  public Object getEntryAt(int i) throws IOException {
    lazyLoad();
    if (i >= nvert)
      throw new ArrayIndexOutOfBoundsException(i);
    // Read the corresponding data in VSet
    DDVSet vset = (DDVSet) hdfFile.retrieveElementByID(
        new DDID(HDFConstants.DFTAG_VS, this.refNo));
    ByteBuffer data = ByteBuffer.wrap(vset.getData());
    int offset = i * ivsize;
    Object[] fields = new Object[types.length];
    for (int iField = 0; iField < fields.length; iField++) {
      switch (types[iField]) {
      case HDFConstants.DFTNT_CHAR:
        fields[iField] = new String(data.array(), offset, sizes[iField]);
        break;
      case HDFConstants.DFTNT_UINT8: fields[iField] = data.get(offset); break;
      case HDFConstants.DFTNT_INT16: fields[iField] = data.getShort(offset); break;
      case HDFConstants.DFTNT_UINT16: fields[iField] = data.getShort(offset) & 0xffff; break;
      case HDFConstants.DFTNT_INT32: fields[iField] = data.getInt(offset); break;
      case HDFConstants.DFTNT_FLOAT: fields[iField] = data.getFloat(offset); break;
      case HDFConstants.DFTNT_DOUBLE: fields[iField] = data.getDouble(offset); break;
      default: throw new RuntimeException("Unsupported type "+types[iField]);
      }
      offset += sizes[iField];
    }
    if (fields.length == 1)
      return fields[0];
    return fields;
  }
  
  @Override
  public String toString() {
    try {
      lazyLoad();
      return String.format("VHeader with %d fields with type %d, size %d, and name '%s', overall name '%s'", types.length, types[0], sizes[0], fieldNames[0], name);
    } catch (IOException e) {
      return "Error reading "+super.toString();
    }
  }

  public String getName() throws IOException {
    lazyLoad();
    return name;
  }


}
