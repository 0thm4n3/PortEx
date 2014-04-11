package com.github.katjahahn.sections.edata

import com.github.katjahahn.PEModule
import com.github.katjahahn.optheader.OptionalHeader
import com.github.katjahahn.sections.SectionLoader
import com.github.katjahahn.PELoader
import java.io.File

class ExportSection(
    private val edataTable: ExportDirTable, 
    private val exportAddressTable: ExportAddressTable, 
    private val namePointerTable: ExportNamePointerTable) extends PEModule {
  
  def getExportDirTable(): ExportDirTable = edataTable

  override def read(): Unit = {}
  
  override def getInfo(): String = 
    s"""|--------------
        |Export Section
        |--------------
        |${edataTable.getInfo}
        |
        |${exportAddressTable.getInfo}
        |
        |${namePointerTable.getInfo}""".stripMargin

}

object ExportSection {
  
  def main(args: Array[String]): Unit = {
    val data = PELoader.loadPE(new File("src/main/resources/testfiles/DLL2.dll"))
    val loader = new SectionLoader(data)
    val edata = loader.loadExportSection()
    println(edata.getInfo())
  }

  def apply(edataBytes: Array[Byte], virtualAddress: Long, 
      opt: OptionalHeader): ExportSection = {
    val edataTable = ExportDirTable(edataBytes)
    val addrTableRVA = edataTable(ExportDirTableKey.EXPORT_ADDR_TABLE_RVA)
    val entries = edataTable(ExportDirTableKey.ADDR_TABLE_ENTRIES).toInt
    val exportAddressTable = ExportAddressTable(edataBytes, addrTableRVA, entries, virtualAddress)
    val nameTableRVA  = edataTable(ExportDirTableKey.NAME_POINTER_RVA)
    val namePointers = edataTable(ExportDirTableKey.NR_OF_NAME_POINTERS).toInt
    val namePointerTable = ExportNamePointerTable(edataBytes, nameTableRVA, namePointers, virtualAddress)
    new ExportSection(edataTable, exportAddressTable, namePointerTable)
  }

  def getInstance(edataBytes: Array[Byte], virtualAddress: Long, 
      opt: OptionalHeader): ExportSection = apply(edataBytes, virtualAddress, opt)
}