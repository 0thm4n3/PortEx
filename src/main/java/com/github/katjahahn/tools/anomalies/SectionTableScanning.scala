package com.github.katjahahn.tools.anomalies

import scala.collection.mutable.ListBuffer
import com.github.katjahahn.optheader.WindowsEntryKey
import scala.collection.JavaConverters._
import com.github.katjahahn.sections.SectionTableEntryKey
import com.github.katjahahn.IOUtil._
import com.github.katjahahn.sections.SectionCharacteristic._
import com.github.katjahahn.sections.SectionCharacteristic
import java.util.Arrays
import com.github.katjahahn.sections.SectionTableEntry

trait SectionTableScanning extends AnomalyScanner {

  //TODO ascending order of VAs

  abstract override def scanReport(): String =
    "Applied Section Table Scanning" + NL + super.scanReport

  abstract override def scan(): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    anomalyList ++= checkFileAlignmentConstrains
    anomalyList ++= checkZeroValues
    anomalyList ++= checkDeprecated
    anomalyList ++= checkReserved
    super.scan ::: anomalyList.toList
  }

  private def checkReserved(): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val sectionTable = data.getSectionTable
    val sections = sectionTable.getSectionEntries.asScala
    for (section <- sections) {
      val characteristics = section.getCharacteristics().asScala
      val entry = section.getEntry(SectionTableEntryKey.CHARACTERISTICS)
      val sectionName = section.getName
      characteristics.foreach(ch =>
        if (ch.getDescription.contains("Reserved")) {
          val description = s"Section Table Entry ${sectionName}: Reserved characteristic used: ${ch.toString}"
          anomalyList += ReservedAnomaly(entry, description)
        })
    }
    anomalyList.toList
  }

  private def checkDeprecated(): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val sectionTable = data.getSectionTable
    val sections = sectionTable.getSectionEntries.asScala
    for (section <- sections) {
      val ptrLineNrEntry = section.getEntry(SectionTableEntryKey.POINTER_TO_LINE_NUMBERS)
      val lineNrEntry = section.getEntry(SectionTableEntryKey.NUMBER_OF_LINE_NUMBERS)
      val sectionName = section.getName
      for (entry <- List(ptrLineNrEntry, lineNrEntry) if entry.value != 0) {
        val description = s"Section Table Entry ${sectionName}: ${entry.key} is deprecated, but has value " + entry.value
        anomalyList += DeprecatedAnomaly(entry, description)
      }
    }
    anomalyList.toList
  }

  private def checkZeroValues(): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val sectionTable = data.getSectionTable()
    val sections = sectionTable.getSectionEntries().asScala
    for (section <- sections) yield {
      val sectionName = section.getName
      checkReloc(anomalyList, section, sectionName)
      checkObjectOnlyCharacteristics(anomalyList, section, sectionName)
      checkUninitializedDataConstraints(anomalyList, section, sectionName)
    }
    anomalyList.toList
  }

  private def checkUninitializedDataConstraints(anomalyList: ListBuffer[Anomaly], section: SectionTableEntry, sectionName: String): Unit = {
    def containsOnlyUnitializedData(): Boolean =
      section.getCharacteristics().contains(IMAGE_SCN_CNT_UNINITIALIZED_DATA) &&
        !section.getCharacteristics().contains(IMAGE_SCN_CNT_INITIALIZED_DATA)

    if (containsOnlyUnitializedData()) {
      val sizeEntry = section.getEntry(SectionTableEntryKey.SIZE_OF_RAW_DATA)
      val pointerEntry = section.getEntry(SectionTableEntryKey.POINTER_TO_RAW_DATA)
      for (entry <- List(sizeEntry, pointerEntry) if entry.value != 0) {
        val value = entry.value
        val description = s"Section Header Entry ${sectionName}: ${entry.key.toString} must be 0 for sections with only uninitialized data, but is: ${value}"
        anomalyList += WrongValueAnomaly(entry, description)
      }
    }
  }

  private def checkFileAlignmentConstrains(): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val fileAlignment = data.getOptionalHeader().get(WindowsEntryKey.FILE_ALIGNMENT)
    val sectionTable = data.getSectionTable()
    val sections = sectionTable.getSectionEntries().asScala
    for (section <- sections) {
      val sizeEntry = section.getEntry(SectionTableEntryKey.SIZE_OF_RAW_DATA)
      val pointerEntry = section.getEntry(SectionTableEntryKey.POINTER_TO_RAW_DATA)
      val sectionName = section.getName
      for (entry <- List(sizeEntry, pointerEntry) if entry.value % fileAlignment != 0) {
        val description = s"Section Table Entry ${sectionName}: ${entry.key} (${entry.value}) must be a multiple of File Alignment (${fileAlignment})"
        anomalyList += WrongValueAnomaly(entry, description)
      }
    }
    anomalyList.toList
  }

  private def checkObjectOnlyCharacteristics(anomalyList: ListBuffer[Anomaly], section: SectionTableEntry, sectionName: String): Unit = {
    val alignmentCharacteristics = Arrays.asList(SectionCharacteristic.values).asScala.filter(k => k.toString.contains("IMAGE_SCN_ALIGN")).toList
    val objectOnly = List(IMAGE_SCN_TYPE_NO_PAD, IMAGE_SCN_LNK_INFO, IMAGE_SCN_LNK_REMOVE, IMAGE_SCN_LNK_COMDAT) ::: alignmentCharacteristics
    for (characteristic <- section.getCharacteristics().asScala if objectOnly.contains(characteristic)) {
      val description = s"Section Table Entry ${sectionName}: ${characteristic} characteristic is only valid for object files"
      val chEntry = section.getEntry(SectionTableEntryKey.CHARACTERISTICS)
      anomalyList += WrongValueAnomaly(chEntry, description)
    }
  }

  private def checkReloc(anomalyList: ListBuffer[Anomaly], section: SectionTableEntry, sectionName: String): Unit = {
    val relocEntry = section.getEntry(SectionTableEntryKey.POINTER_TO_RELOCATIONS)
    val nrRelocEntry = section.getEntry(SectionTableEntryKey.NUMBER_OF_RELOCATIONS)
    for (entry <- List(relocEntry, nrRelocEntry) if entry.value != 0) {
      val description = s"Section Table Entry ${sectionName}: ${entry.key} should be 0 for images, but has value " + entry.value
      anomalyList += DeprecatedAnomaly(entry, description)
    }
  }
}