/**
 * *****************************************************************************
 * Copyright 2014 Katja Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.katjahahn.parser.sections.rsrc
import scala.collection.JavaConverters._
import com.github.katjahahn.parser.ByteArrayUtil._
import com.github.katjahahn.parser.StandardField
import com.github.katjahahn.parser.IOUtil
import com.github.katjahahn.parser.MemoryMappedPE

class ResourceDataEntry(val data: Map[ResourceDataEntryKey, StandardField]) {
  override def toString(): String =
    s"""data entry
       |..........
       |
       |${data.values.map(_.toString()).mkString("\n")}
       |""".stripMargin

  def readResourceBytes(virtualAddress: Long, mmBytes: MemoryMappedPE): Array[Byte] = {
    val address = data(ResourceDataEntryKey.DATA_RVA).value
    val length = data(ResourceDataEntryKey.SIZE).value + address
    mmBytes.slice(address, length)
  }

}

object ResourceDataEntry {
  val size = 16
  private val specLocation = "resourcedataentryspec"

  /**
   * @param entryBytes the byte array containing the entry
   * @param entryOffset the file offset of the entry start
   */
  def apply(entryBytes: Array[Byte], entryOffset: Long): ResourceDataEntry = {
    val spec = IOUtil.readMap(specLocation).asScala.toMap
    val data = for ((sKey, sVal) <- spec) yield {
      val key = ResourceDataEntryKey.valueOf(sKey)
      val relFieldOffset = Integer.parseInt(sVal(1))
      val length = Integer.parseInt(sVal(2))
      if (relFieldOffset + length > entryBytes.length) {
        throw new IllegalArgumentException("unable to read resource data entry")
      }
      val value = getBytesLongValue(entryBytes, relFieldOffset, length)
      val description = sVal(0)
      val absFieldOffset = relFieldOffset + entryOffset
      (key, new StandardField(key, description, value, absFieldOffset, length))
    }
    new ResourceDataEntry(data)
  }
}
