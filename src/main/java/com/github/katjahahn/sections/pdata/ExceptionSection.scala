package com.github.katjahahn.sections.pdata

import com.github.katjahahn.sections.SpecialSection
import com.github.katjahahn.coffheader.MachineType
import com.github.katjahahn.coffheader.MachineType._
import ExceptionSection._
import com.github.katjahahn.ByteArrayUtil._
import com.github.katjahahn.IOUtil._
import scala.collection.JavaConverters._
import com.github.katjahahn.StandardField
import Function.tupled
import scala.collection.mutable.ListBuffer
import com.github.katjahahn.sections.debug.DebugDirTableKey
import java.io.File
import com.github.katjahahn.PELoader
import com.github.katjahahn.sections.SectionLoader
import com.github.katjahahn.optheader.DataDirectoryKey
import com.github.katjahahn.IOUtil

class ExceptionSection private (
  offset: Long,
  private val directory: ExceptionDirectory) extends SpecialSection {

  def getField(key: ExceptionEntryKey): StandardField = directory(key)

  def get(key: ExceptionEntryKey): Long = directory(key).value

  def getExceptionFields(): java.util.Map[ExceptionEntryKey, StandardField] = directory.asJava

  def getInfo(): String = directory.values.mkString(NL)

  def getOffset(): Long = offset
}

object ExceptionSection {

  type ExceptionDirectory = Map[ExceptionEntryKey, StandardField]

  private val mipsspec = "pdatamipsspec"
  private val wincespec = "pdatawincespec"
  private val x64spec = "pdatax64spec"
  private val armv7spec = "pdataarmv7spec"

  private val machineToSpec =
    Map(ARM -> wincespec, POWERPC -> wincespec, SH3 -> wincespec,
      SH3DSP -> wincespec, SH4 -> wincespec, THUMB -> wincespec,

      R4000 -> mipsspec, MIPS16 -> mipsspec, MIPSFPU -> mipsspec,
      MIPSFPU16 -> mipsspec, WCEMIPSV2 -> mipsspec,

      AMD64 -> x64spec, IA64 -> x64spec,
      ARMNT -> armv7spec)

  //TODO wincespec!
  def apply(sectionbytes: Array[Byte], machine: MachineType,
    virtualAddress: Long, offset: Long): ExceptionSection = {
    if(!machineToSpec.contains(machine)) {
      throw new IllegalArgumentException("spec for machine type not found: " + machine)
    }
    val spec = machineToSpec(machine)
    println("using spec: " + spec)
    val format = new SpecificationFormat(0,1,2,3)
    val directory = IOUtil.readHeaderEntries(classOf[ExceptionEntryKey], 
        format, spec, sectionbytes.clone).asScala.toMap
    new ExceptionSection(offset, directory)
  }

  def newInstance(sectionbytes: Array[Byte], machine: MachineType,
    virtualAddress: Long, offset: Long): ExceptionSection =
    apply(sectionbytes, machine, virtualAddress, offset)

  def main(args: Array[String]): Unit = {
    val folder = new File("src/main/resources/testfiles/")
    for (file <- folder.listFiles) {
      val data = PELoader.loadPE(file)
      val entries = data.getOptionalHeader().getDataDirEntries()
      if (entries.containsKey(DataDirectoryKey.EXCEPTION_TABLE)) {
        try {
          val pdata = new SectionLoader(data).loadExceptionSection()
          println("file: " + file.getName)
          println(pdata.getInfo)
          println()
        } catch {
          case e: IllegalStateException => println(e.getMessage())
        }
      }
    }
  }

}