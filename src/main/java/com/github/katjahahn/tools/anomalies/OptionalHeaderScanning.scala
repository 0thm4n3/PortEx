package com.github.katjahahn.tools.anomalies

import scala.collection.mutable.ListBuffer
import com.github.katjahahn.coffheader.Characteristic
import com.github.katjahahn.coffheader.Characteristic._
import com.github.katjahahn.optheader.OptionalHeader
import com.github.katjahahn.PEData
import com.github.katjahahn.optheader.DataDirectoryKey
import java.util.Map
import com.github.katjahahn.IOUtil
import com.github.katjahahn.optheader.Subsystem
import com.github.katjahahn.optheader.WindowsEntryKey
import com.github.katjahahn.optheader.StandardFieldEntryKey

trait OptionalHeaderScanning extends AnomalyScanner {
  
  abstract override def scan(): List[Anomaly] = {
    val opt = data.getOptionalHeader()
    val anomalyList = ListBuffer[Anomaly]()
    if (opt == null) return Nil
    anomalyList ++= dataDirScan(opt)
    anomalyList ++= windowsFieldScan(opt)
    super.scan ::: anomalyList.toList
  }

  private def windowsFieldScan(opt: OptionalHeader): List[Anomaly] = {
    checkImageBase(opt) ::: checkSectionAlignment(opt) ::: checkReserved(opt) ::: checkSizes(opt)
  }
  
  private def checkSizes(opt: OptionalHeader): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val imageSize = opt.get(WindowsEntryKey.SIZE_OF_IMAGE)
    val headerSize = opt.get(WindowsEntryKey.SIZE_OF_HEADERS)
    val sectionAlignment = opt.get(WindowsEntryKey.SECTION_ALIGNMENT)
    val fileAlignment = opt.get(WindowsEntryKey.FILE_ALIGNMENT)
    if(imageSize % sectionAlignment != 0) {
      val entry = opt.getWindowsFieldEntry(WindowsEntryKey.SIZE_OF_IMAGE)
      val description = s"Optional Header: Size of Image (${imageSize}) must be a multiple of Section Alignment (${sectionAlignment})"
      anomalyList += WrongValueAnomaly(entry, description)
    }
    if(headerSize % fileAlignment != 0) {
      val entry = opt.getWindowsFieldEntry(WindowsEntryKey.SIZE_OF_IMAGE)
      val description = s"Optional Header: Size of Headers (${headerSize}) must be a multiple of File Alignment (${fileAlignment})"
      anomalyList += WrongValueAnomaly(entry, description)
    } //TODO headerSize >= MSDOS + PEHeader + Section Headers size
    anomalyList.toList
  }

  private def checkReserved(opt: OptionalHeader): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val win32version = opt.get(WindowsEntryKey.WIN32_VERSION_VALUE)
    val loaderFlags = opt.get(WindowsEntryKey.LOADER_FLAGS)
    if (win32version != 0) {
      val description = "Optional Header: Reserved WIN32_VERSION_VALUE is not 0, but " + win32version
      val entry = opt.getWindowsFieldEntry(WindowsEntryKey.WIN32_VERSION_VALUE)
      List(ReservedAnomaly(entry, description))
    } 
    if(loaderFlags != 0) {
      val description = "Optional Header: Reserved Loader Flags is not 0, but " + loaderFlags
      val entry = opt.getWindowsFieldEntry(WindowsEntryKey.LOADER_FLAGS)
      List(ReservedAnomaly(entry, description))
    }
    anomalyList.toList
  }

  private def checkFileAlignment(opt: OptionalHeader): List[Anomaly] = {
    //TODO
    def isPowerOfTwo(x: Long): Boolean = (x != 0) && ((x & (x - 1)) == 0)
    val anomalyList = ListBuffer[Anomaly]()
    val sectionAlignment = opt.get(WindowsEntryKey.SECTION_ALIGNMENT)
    val entry = opt.getWindowsFieldEntry(WindowsEntryKey.FILE_ALIGNMENT)
    val fileAlignment = entry.value
    if (!isPowerOfTwo(fileAlignment)) {
      val description = "Optional Header: File Alignment must be a power of 2, but is " + fileAlignment
      anomalyList += WrongValueAnomaly(entry, description)
    }
    if (fileAlignment < 512 || fileAlignment > 65536) {
      val description = "Optional Header: File Alignment must be between 512 and 64 K, but is " + fileAlignment
      anomalyList += WrongValueAnomaly(entry, description)
    }
    if (fileAlignment != 512) {
      val description = "Optional Header: Default File Alignment is 512, but actual value is " + fileAlignment
      anomalyList += WrongValueAnomaly(entry, description)
    }
    anomalyList.toList
  }

  private def checkSectionAlignment(opt: OptionalHeader): List[Anomaly] = {
    val sectionAlignment = opt.get(WindowsEntryKey.SECTION_ALIGNMENT)
    val fileAlignment = opt.get(WindowsEntryKey.FILE_ALIGNMENT)
    if (sectionAlignment < fileAlignment) {
      val description = s"Optional Header: Section Alignment (${sectionAlignment}) needs to be >= File Alignment (${fileAlignment})"
      val entry = opt.getWindowsFieldEntry(WindowsEntryKey.SECTION_ALIGNMENT)
      List(WrongValueAnomaly(entry, description))
    } else Nil
  }

  private def checkImageBase(opt: OptionalHeader): List[Anomaly] = {
    val anomalyList = ListBuffer[Anomaly]()
    val entry = opt.getWindowsFieldEntry(WindowsEntryKey.IMAGE_BASE)
    val imageBase = entry.value
    if (imageBase % 65536 != 0) {
      val description = "Image Base must be a multiple of 64 K, but is " + imageBase
      anomalyList += WrongValueAnomaly(entry, description)
    }
    if (isDLL() && imageBase != 0x10000000) {
      val description = "The default image base for a DLL is 0x10000000, but actual value is 0x" + java.lang.Long.toHexString(imageBase)
      anomalyList += NonDefaultAnomaly(entry, description)
    } else if (isWinCE() && imageBase != 0x00010000) {
      val description = "The default image base for Win CE EXE is 0x00010000, but actual value is 0x" + java.lang.Long.toHexString(imageBase)
      anomalyList += NonDefaultAnomaly(entry, description)
    } //else if(imageBase != 0x00400000) { TODO
    //      val description = "The default image base is 0x00400000, but actual value is 0x" + java.lang.Long.toHexString(imageBase) 
    //      anomalyList += NonDefaultAnomaly(entry, description)
    //    }
    anomalyList.toList
  }

  private def isWinCE(): Boolean =
    data.getOptionalHeader().getSubsystem() == Subsystem.IMAGE_SUBSYSTEM_WINDOWS_CE_GUI

  private def isDLL(): Boolean =
    data.getCOFFFileHeader().getCharacteristics().contains(IMAGE_FILE_DLL)

  private def dataDirScan(opt: OptionalHeader): List[Anomaly] = {
    val datadirs = opt.getDataDirEntries()
    if (datadirs.containsKey(DataDirectoryKey.RESERVED)) {
      val entry = datadirs.get(DataDirectoryKey.RESERVED)
      val description = "Reserved Data Directory Entry is not 0. Entry --> " + IOUtil.NL + entry.toString
      List(ReservedDataDirAnomaly(entry, description))
    } else Nil
  }

}