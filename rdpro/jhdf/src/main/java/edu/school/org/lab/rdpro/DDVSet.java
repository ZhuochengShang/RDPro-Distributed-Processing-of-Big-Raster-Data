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

/**
 * @author Ahmed Eldawy
 */
public class DDVSet extends DataDescriptor {

  /**
   * The data stored in this set. This data can be interpreted only
   * according to the corresponding {@link DDVDataHeader}
   */
  protected byte[] data;

  DDVSet(HDFFile hdfFile, int tagID, int refNo, int offset, int length,
      boolean extended) {
    super(hdfFile, tagID, refNo, offset, length, extended);
  }

  @Override
  protected void readFields(DataInput input) throws IOException {
    this.data = new byte[getLength()];
    input.readFully(data);
  }
  
  byte[] getData() throws IOException {
    lazyLoad();
    return data;
  }
  
  @Override
  public String toString() {
    try {
      lazyLoad();
      return String.format("VSet of total size %d", data.length);
    } catch (IOException e) {
      return "Error loading "+super.toString();
    }
  }
}
