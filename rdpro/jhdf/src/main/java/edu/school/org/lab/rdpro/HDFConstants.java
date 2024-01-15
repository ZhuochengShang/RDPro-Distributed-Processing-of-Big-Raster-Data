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

public final class HDFConstants {

  /* Tags and refs*/
  public static final int DFTAG_NONE = 0;
  public static final int DFTAG_NULL = 1;
  /** linked-block special element */
  public static final int DFTAG_LINKED = 20;
  /** Version of the program that write the HDF file */
  public static final int DFTAG_VERSION = 30;
  /** compressed special element */
  public static final int DFTAG_COMPRESSED = 40;
  /** variable-len linked-block header */
  public static final int DFTAG_VLINKED = 50;
  /** variable-len linked-block data */
  public static final int DFTAG_VLINKED_DATA = 51;
  /** chunked special element header (for expansion, not used ) */
  public static final int DFTAG_CHUNKED = 60;
  /** chunk element */
  public static final int DFTAG_CHUNK = 61;
  /* Utility set */
  /** File identifier */
  public static final int DFTAG_FID = 100;
  /** File description */
  public static final int DFTAG_FD = 101;
  /**  */
  public static final int DFTAG_TID = 102;
  /**  */
  public static final int DFTAG_TD = 103;
  /**  */
  public static final int DFTAG_DIL = 104;
  /**  */
  public static final int DFTAG_DIA = 105;
  /**  */
  public static final int DFTAG_NT = 106;
  /**  */
  public static final int DFTAG_MT = 107;
  /**  */
  public static final int DFTAG_FREE = 108;
  /* Raster-8 set */
  /**  */
  public static final int DFTAG_ID8 = 200;
  /**  */
  public static final int DFTAG_IP8 = 201;
  /**  */
  public static final int DFTAG_RI8 = 202;
  /**  */
  public static final int DFTAG_CI8 = 203;
  /**  */
  public static final int DFTAG_II8 = 204;
  /* Raster image set */
  /**  */
  public static final int DFTAG_ID = 300;
  /**  */
  public static final int DFTAG_LUT = 301;
  /**  */
  public static final int DFTAG_RI = 302;
  /**  */
  public static final int DFTAG_CI = 303;
  /**  */
  public static final int DFTAG_NRI = 304;

  /** Raster Image Group */
  public static final int DFTAG_RIG = 306;
  /** Palette DimRec */
  public static final int DFTAG_LD = 307;
  /** Matte DimRec */
  public static final int DFTAG_MD = 308;
  /** Matte Data */
  public static final int DFTAG_MA = 309;
  /** color correction */
  public static final int DFTAG_CCN = 310;
  /** color format */
  public static final int DFTAG_CFM = 311;
  /** aspect ratio */
  public static final int DFTAG_AR = 312;

  /** Draw these images in sequence */
  public static final int DFTAG_DRAW = 400;
  /** run this as a program/script */
  public static final int DFTAG_RUN = 401;
  /** x-y position */
  public static final int DFTAG_XYP = 500;
  /** machine-type override */
  public static final int DFTAG_MTO = 501;

  /* Tektronix */
  /** TEK 4014 data */
  public static final int DFTAG_T14 = 602;
  /** TEK 4105 data */
  public static final int DFTAG_T105 = 603;

  /* Scientific dataset */
  /** Scientific Data Group */
  public static final int DFTAG_SDG = 700;
  /** Scientific Data Dimension Record */
  public static final int DFTAG_SDD = 701;
  /** Scientific Data */
  public static final int DFTAG_SD = 702;
  /** Scales */
  public static final int DFTAG_SDS = 703;
  /** Units */
  public static final int DFTAG_SDL = 704;
  /** Units */
  public static final int DFTAG_SDU = 705;
  /** Formats */
  public static final int DFTAG_SDF = 706;
  /** Max/Min */
  public static final int DFTAG_SDM = 707;
  /** Coord sys */
  public static final int DFTAG_SDC = 708;
  /** Transpose */
  public static final int DFTAG_SDT = 709;
  /** Links related to the dataset */
  public static final int DFTAG_SDLNK = 710;
  /** Numeric Data Group */
  public static final int DFTAG_NDG = 720;
  /** Calibration information */
  public static final int DFTAG_CAL = 731;
  /** Fill Value information */
  public static final int DFTAG_FV = 732;
  /** Beginning of required tags */
  public static final int DFTAG_BREQ = 799;
  /** Current end of the range */
  public static final int DFTAG_EREQ = 780;
  /** List of ragged array line lengths */
  public static final int DFTAG_SDRAG = 781;

  /* VSets */
  public static final int DFTAG_VG = 1965;
  public static final int DFTAG_VH = 1962;
  public static final int DFTAG_VS = 1963;
  
  /* Compression schemes */
  public static final int DFTAG_RLE = 11;
  public static final int DFTAG_IMC = 12;
  public static final int DFTAG_IMCOMP = 12;
  public static final int DFTAG_JPEG = 13;
  public static final int DFTAG_GREYJPED = 14;

  /** A marker of extended tags */ 
  public static final int DFTAG_EXTENDED = 0x4000;

  /* Special codes for extended blocks */
  /** Fixed-size Linked blocks */
  public static final int SPECIAL_LINKED = 1;
  /** External */
  public static final int SPECIAL_EXT = 2;    
  /** Compressed */
  public static final int SPECIAL_COMP = 3;     
  /** Variable-length linked blocks */
  public static final int SPECIAL_VLINKED = 4;   
  /** chunked element */
  public static final int SPECIAL_CHUNKED = 5;
  /** Buffered element */
  public static final int SPECIAL_BUFFERED = 6;
  /** Compressed Raster element */
  public static final int SPECIAL_COMPRAS = 7;
  
  /* Compression types */
  
  public static final int COMP_CODE_NONE = 0;
  public static final int COMP_CODE_RLE = 1;
  public static final int COMP_CODE_NBIT = 2;
  public static final int COMP_CODE_SKPHUFF = 3;
  public static final int COMP_CODE_DEFLATE = 4;

  /**
   * Data Types
   * @see <a href="https://support.hdfgroup.org/ftp/HDF/releases/HDF4.2.13/src/hdf4_java_doc/hdf/hdflib/HDFConstants.html"></a>
   */
  public static final int DFTNT_HDF = 0;
  /** character */
  public static final int DFTNT_CHAR = 4;
  public static final int DFTNT_CHAR8 = 4;
  public static final int DFTNT_CHAR16 = 42;
  /** Unsigned 8-bit integer */
  public static final int DFTNT_UINT8 = 21;
  /** short */
  public static final int DFTNT_INT16 = 22;
  /** Unsigned integer */
  public static final int DFTNT_UINT16 = 23;
  /** Signed integer */
  public static final int DFTNT_INT32 = 24;
  public static final int DFTNT_CUSTOM = 8192;

  public static final int DFTNT_FLOAT = 5;
  public static final int DFTNT_FLOAT32 = 5;
  public static final int DFTNT_FLOAT64 = 6;
  public static final int DFTNT_DOUBLE = 6;
  public static final int DFTNT_FLOAT128 = 7;

  public static final String[] TagNames = new String[32768];
  
  static {
    TagNames[DFTAG_NONE] = "DFTAG_NONE";
    TagNames[DFTAG_NULL] = "DFTAG_NULL";
    TagNames[DFTAG_LINKED] = "DFTAG_LINKED";
    TagNames[DFTAG_VERSION] = "DFTAG_VERSION";
    TagNames[DFTAG_COMPRESSED] = "DFTAG_COMPRESSED";
    TagNames[DFTAG_SD] = "DFTAG_SD";
  }

  public static int readAsInteger(byte[] bytes, int offset, int length) {
    if (length > 4)
      throw new RuntimeException("Value too long");
    int value = 0;
    while (length-- > 0) {
      int byteValue = bytes[offset++] & 0xff;
      value = (value << 8) | byteValue;
    }
    return value;
  }
  
  /**
   * Writes a numeric value of any size at the given position. The length
   * determines number of bytes that should be written there
   * @param bytes
   * @param offset
   * @param value
   * @param length
   */
  public static void writeAt(byte[] bytes, int offset, int value, int length) {
    while (length-- > 0) {
      bytes[offset + length] = (byte)(value & 0xff);
      value >>>= 8;
    }
  }
}
