/*
 * Copyright 2021 '""
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
package edu.school.org.lab.rdpro.util;

import java.io.IOException;
import java.io.InputStream;

public class BitInputStream2 implements AutoCloseable {

  /**The underlying input stream*/
  private final InputStream in;

  /**A copy of the eight bytes currently being inspected*/
  private long currentLong;

  /**The number of remaining bits (least significant) in the current long*/
  private int remainingBitsInCurrentLong;

  public BitInputStream2(InputStream in) {
    this.in = in;
  }

  public final short nextShort(int desiredNumBits) throws IOException {
    while (desiredNumBits > remainingBitsInCurrentLong) {
      currentLong = (currentLong << 8) | (this.in.read() & 0xffL);
      remainingBitsInCurrentLong += 8;
    }
    short value = (short) (currentLong >>> (remainingBitsInCurrentLong -= desiredNumBits));
    return (short) (value& (0xffff >> (16 - desiredNumBits)));
  }

  @Override
  public void close() throws IOException {
    in.close();
  }
}
