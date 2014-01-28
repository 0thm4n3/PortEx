package com.github.katjahahn.sections.idata

import scala.collection.mutable.ListBuffer
import com.github.katjahahn.PEModule
import com.github.katjahahn.IOUtil
import com.github.katjahahn.StandardEntry
import scala.collection.JavaConverters._
import com.github.katjahahn.PEModule._
import com.github.katjahahn.sections.idata.IDataEntryKey._
import com.github.katjahahn.ByteArrayUtil._

class IDataEntry(private val entrybytes: Array[Byte],
    private val specification: Map[String, Array[String]],
    private val entries: Map[IDataEntryKey, StandardEntry]) extends PEModule {

  private var lookupTableEntries: List[LookupTableEntry] = Nil
  var name: String = _

  def addLookupTableEntry(e: LookupTableEntry): Unit = {
    lookupTableEntries = lookupTableEntries :+ e
  }

  /**
   * No use here, because object is used as factory instead
   */
  override def read(): Unit = {}

  def apply(key: IDataEntryKey): Int = {
    entries(key).value
  }

  override def getInfo(): String = s"""${entries.values.mkString(NL)} 
  |ASCII name: $name
  |
  |lookup table entries for $name
  |--------------------------------------
  |
  |${lookupTableEntries.mkString(NL)}""".stripMargin

  override def toString(): String = getInfo()

}

object IDataEntry {

  def apply(entrybytes: Array[Byte], specLocation: String): IDataEntry = {
    val specification = IOUtil.readMap(specLocation).asScala.toMap
    val buffer = ListBuffer.empty[StandardEntry]
    for ((key, specs) <- specification) {
      val description = specs(0)
      val offset = Integer.parseInt(specs(1))
      val size = Integer.parseInt(specs(2))
      val value = getBytesIntValue(entrybytes, offset, size)
      val entry = new StandardEntry(key, description, value)
      buffer += entry
    }
    val entries: Map[IDataEntryKey, StandardEntry] = (buffer map { t => (IDataEntryKey.withName(t.key), t) }).toMap;
    new IDataEntry(entrybytes, specification, entries)
  }
}