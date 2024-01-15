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
import java.util.Arrays;

/**
 * Scientific data dimension record.
 * tagID =  DFTAG_SDD (701)
 * @author Ahmed Eldawy
 *
 */
public class DDScientificDDR extends DataDescriptor {

  /**Number of values along each dimension*/
  protected int[] dimensions;
  /**Reference number of DFTAG_NT for data*/
  protected int data_NT_ref;
  /**Reference number of DFTAG_NT for the scale of each dimension*/
  protected int[] scale_NT_refs;
  
  DDScientificDDR(HDFFile hdfFile, int tagID, int refNo, int offset,
      int length, boolean extended) {
    super(hdfFile, tagID, refNo, offset, length, extended);
  }

  @Override
  protected void readFields(DataInput input) throws IOException {
    // Number of dimensions
    int rank = input.readUnsignedShort();
    this.dimensions = new int[rank];
    for (int iDim = 0; iDim < rank; iDim++)
      this.dimensions[iDim] = input.readInt();
    int marker = input.readUnsignedShort();
    if (marker != HDFConstants.DFTAG_NT)
      throw new RuntimeException("Found "+marker+" instead of DFTAG_NT");
    this.data_NT_ref = input.readUnsignedShort();
    this.scale_NT_refs = new int[rank];
    for (int iDim = 0; iDim < rank; iDim++) {
      marker = input.readUnsignedShort();
      if (marker != HDFConstants.DFTAG_NT)
        throw new RuntimeException("Did not find DFTAG_NT but "+marker);
      this.scale_NT_refs[iDim] = input.readUnsignedShort();
    }
  }
  
  /**
   * Returns the scientific data associated with this dimension record
   * @return
   * @throws IOException
   */
  public DDNumberType getNumberType() throws IOException {
    lazyLoad();
    return (DDNumberType) hdfFile.retrieveElementByID(
        new DDID(HDFConstants.DFTAG_NT, data_NT_ref));
  }
  
  @Override
  public String toString() {
    try {
      lazyLoad();
      return String.format("Scientific data dimension record with dimensions %s", Arrays.toString(dimensions));
    } catch (IOException e) {
      return "Error loading "+super.toString();
    }
  }

  public int[] getDimensions() throws IOException {
    lazyLoad();
    return dimensions;
  }
}
