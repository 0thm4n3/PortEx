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
package com.github.katjahahn.sections.idata

import com.github.katjahahn.sections.PESection
import com.github.katjahahn.IOUtil
import ImportSection._
import com.github.katjahahn.StandardEntry
import scala.collection.JavaConverters._
import com.github.katjahahn.StandardDataEntry
import com.github.katjahahn.PEModule._
import ImportDirEntryKey._
import com.github.katjahahn.optheader.OptionalHeader
import com.github.katjahahn.optheader.OptionalHeader.MagicNumber._
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the import section, fetches information about the data directory
 * entries and their lookup table entries.
 *
 * The instance of this class is usually created by the section loader.
 *
 * @constructor
 * @param idatabytes the bytes that belong to the import section
 * @param virtualAddress the address all rva values in the import section are relative to
 * @param optHeader the optional header of the file
 * @author Katja Hahn
 */
class ImportSection(
  private val idatabytes: Array[Byte],
  private val virtualAddress: Long,
  private val optHeader: OptionalHeader) extends PESection {

  //TODO set bytes for superclass

  private var directoryTable = List.empty[ImportDirTableEntry]

  override def read(): Unit = {
    readDirEntries()
    readLookupTableEntries()
  }

  /**
   * Returns the directory table entries of the import section.
   * Each entry contains the lookup table entries that belong to it.
   *
   * @return a list of the directory table entries of the import section
   */
  def getDirectoryTable(): java.util.List[ImportDirTableEntry] = directoryTable.asJava

  private def readLookupTableEntries(): Unit = {
    for (dirEntry <- directoryTable) {
      var entry: LookupTableEntry = null
      var iRVA = dirEntry(I_LOOKUP_TABLE_RVA)
      if (iRVA == 0) iRVA = dirEntry(I_ADDR_TABLE_RVA)
      var offset = iRVA - virtualAddress
      val EntrySize = optHeader.getMagicNumber match {
        case PE32 => 4
        case PE32_PLUS => 8
        case ROM => throw new IllegalArgumentException("ROM file format not described")
      }
      do {
        entry = LookupTableEntry(idatabytes.clone, offset.toInt, EntrySize, virtualAddress)
        if (!entry.isInstanceOf[NullEntry]) dirEntry.addLookupTableEntry(entry)
        offset += EntrySize
      } while (!entry.isInstanceOf[NullEntry])
    }
  }

  private def readDirEntries(): Unit = {
    var isLastEntry = false
    var i = 0
    do {
      readDirEntry(i) match {
        case Some(entry) =>
          logger.debug("------------start-----------")
          logger.debug("dir entry read: " + entry)
          logger.debug("------------end-------------")
          directoryTable = directoryTable :+ entry
        case None => isLastEntry = true
      }
      i += 1
    } while (!isLastEntry)
  }

  private def readDirEntry(nr: Int): Option[ImportDirTableEntry] = {
    val from = nr * ENTRY_SIZE
    val until = from + ENTRY_SIZE
    val entrybytes = idatabytes.slice(from, until)

    //TODO this condition is wrong (?)
    def isEmpty(entry: ImportDirTableEntry): Boolean =
      //      entry.entries.values.forall(v => v == 0)
      entry(I_LOOKUP_TABLE_RVA) == 0 && entry(I_ADDR_TABLE_RVA) == 0

    val entry = ImportDirTableEntry(entrybytes, I_DIR_ENTRY_SPEC)
    entry.name = getASCIIName(entry)
    if (isEmpty(entry)) None else
      Some(entry)
  }

  private def entryDescription(): String =
    (for (e <- directoryTable)
      yield e.getInfo() + IOUtil.NL + IOUtil.NL).mkString

  private def getASCIIName(entry: ImportDirTableEntry): String = {
    def getName(value: Int): String = {
      val offset = value - virtualAddress
      //TODO cast to int is insecure. actual int is unsigned, java int is signed
      val nullindex = idatabytes.indexWhere(b => b == 0, offset.toInt)
      new String(idatabytes.slice(offset.toInt, nullindex))
    }
    getName(entry(NAME_RVA).toInt)
  }

  /**
   * Returns a decription of all entries in the import section.
   * 
   * @return a description of all entries in the import section
   */
  override def getInfo(): String =
    s"""|--------------
	|Import section
	|--------------
    |
    |$entryDescription""".stripMargin

}

object ImportSection {

  private final val I_DIR_ENTRY_SPEC = "idataentryspec"
  private final val HINT_NAME_TABLE_SPEC = "hintnametablespec"
  private final val ENTRY_SIZE = 20

  private final val logger = LogManager.getLogger(ImportSection.getClass().getName())
}
