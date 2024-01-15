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
 * Data descriptor for the library version number. It contains the complete
 * version number and a descriptive string for the latest version of the HDF
 * library used to write the file.
 * @author ''
 *
 */
public class DDVersion extends DataDescriptor {

  /** Major version number */
  public int majorVersion;
  
  /** Minor version number */
  public int minorVersion;
  
  /** Release number */
  public int release;
  
  /**
   * A descriptive string for the latest version of the HDF library used to
   * write to the file
   */
  public String name;

  DDVersion(HDFFile hdfFile, int tagID, int refNo, int offset, int length,
      boolean extended) {
    super(hdfFile, tagID, refNo, offset, length, extended);
  }

  @Override
  protected void readFields(DataInput input) throws IOException {
    this.majorVersion = input.readInt();
    this.minorVersion = input.readInt();
    this.release = input.readInt();
    byte[] nameBytes = new byte[getLength() - 12];
    input.readFully(nameBytes);
    name = new String(nameBytes);
  }
  
  @Override
  public String toString() {
    try {
      lazyLoad();
      return String.format("Version %d.%d.%d '%s'", majorVersion, minorVersion, release, name);
    } catch (IOException e) {
      return "Error loading "+super.toString();
    }
  }

}
