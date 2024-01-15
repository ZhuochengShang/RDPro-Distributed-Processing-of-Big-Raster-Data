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

package edu.school.org.lab.rdpro;

import java.io.DataInput;
import java.io.IOException;

/**
 * Data descriptor for array of scientific data.
 * @author
 *
 */
public class DDScientificData extends DataDescriptor {

  /**Raw data*/
  public byte[] data;
  
  DDScientificData(HDFFile hdfFile, int tagID, int refNo, int offset,
      int length, boolean extended) {
    super(hdfFile, tagID, refNo, offset, length, extended);
  }

  @Override
  protected void readFields(DataInput input) throws IOException {
    data = new byte[getLength()];
    input.readFully(data);
  }

  @Override
  public String toString() {
    try {
      lazyLoad();
      return String.format("Scientific data of size %d", getLength());
    } catch (IOException e) {
      return "Error loading "+super.toString();
    }
  }

  public byte[] getData() throws IOException {
    lazyLoad();
    return data;
  }
}
