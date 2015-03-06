package com.github.katjahahn.parser.sections.rsrc.version

import java.io.RandomAccessFile
import com.github.katjahahn.parser.IOUtil._
import com.github.katjahahn.parser.ScalaIOUtil.{ using, hex }
import com.github.katjahahn.parser.ByteArrayUtil

class VsFixedFileInfo(
  val dwSignature: Long,
  val wMajorStrucVersion: Int,
  val wMinorStrucVersion: Int,
  val dwFileVersionMS: Long,
  val dwFileVersionLS: Long,
  val dwProductVersionMS: Long,
  val dwProductVersionLS: Long,
  val dwFileFlagsMask: Long,
  val dwFileFlags: Long,
  val fileOS: List[FileOS],
  val fileType: FileType,
  val fileSubtype: FileSubtype,
  val dwFileDateMS: Long,
  val dwFileDateLS: Long) {

  override def toString(): String = 
    s"""|signature: ${hex(dwSignature)}
  |binary version:  ${wMajorStrucVersion}.${wMinorStrucVersion}
  |file version: ${dwFileVersionMS}.${dwFileVersionLS}
  |product version: ${dwProductVersionMS}.${dwProductVersionLS}
  |file flags mask: ${hex(dwFileFlagsMask)}
  |file flags: ${hex(dwFileFlags)}
  |file OS: ${fileOS.map(_.getDescription).mkString(", ")}
  |file type: ${fileType.getDescription}
  |file subtype: ${fileSubtype.getDescription}
  |file date: ${dwFileDateMS}.${dwFileDateLS}""".stripMargin
  

}

object VsFixedFileInfo {

  //TODO move to utility class
  private val byteSize = 1
  private val wordSize = 2
  private val dwordSize = 4
  private val qwordSize = 8

  def apply(offset: Long, wValueLength: Int, raf: RandomAccessFile): VsFixedFileInfo = {
    val boundary = 32
    val padding = loadBytes(offset, boundary, raf).toList.indexWhere(0 !=)
    val dwSignature = ByteArrayUtil.bytesToLong(loadBytes(offset + padding, dwordSize, raf))
    //TODO version correct? check this
    val wMinorVersion = ByteArrayUtil.bytesToInt(loadBytes(offset + padding + dwordSize, wordSize, raf))
    val wMajorVersion = ByteArrayUtil.bytesToInt(loadBytes(offset + padding + dwordSize + wordSize, wordSize, raf))
    val dwFileVersionMS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 2, dwordSize, raf))
    val dwFileVersionLS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 3, dwordSize, raf))
    val dwProductVersionMS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 4, dwordSize, raf))
    val dwProductVersionLS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 5, dwordSize, raf))
    val dwFileFlagsMask = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 6, dwordSize, raf))
    val dwFileFlags = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 7, dwordSize, raf))
    val dwFileOS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 8, dwordSize, raf))
    //TODO use mask to collect all that fit
    val fileOS = FileOS.values.toBuffer.filter { os => (os.getValue & dwFileOS) != 0 }
    if(fileOS.isEmpty) fileOS += FileOS.VOS_UNKNOWN
    val dwFileType = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 9, dwordSize, raf))
    val fileType = FileType.values.find(_.getValue == dwFileType).getOrElse(FileType.VFT_UNKNOWN)
    val dwFileSubtype = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 10, dwordSize, raf))
    val fileSubtype = {
      if(fileType == FileType.VFT_DRV) 
        DrvFileSubtype.values.find(_.getValue == dwFileSubtype).getOrElse(new UndefinedSubtype(dwFileSubtype, true))
      else if (fileType == FileType.VFT_FONT)
        FontFileSubtype.values.find(_.getValue == dwFileSubtype).getOrElse(new UndefinedSubtype(dwFileSubtype, true))
      else new UndefinedSubtype(dwFileSubtype)
    }
    val dwFileDateMS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 11, dwordSize, raf))
    val dwFileDateLS = ByteArrayUtil.bytesToLong(loadBytes(offset + padding + dwordSize * 12, dwordSize, raf))
    new VsFixedFileInfo(dwSignature, wMajorVersion, wMinorVersion, dwFileVersionMS,
      dwFileVersionLS, dwProductVersionMS, dwProductVersionLS, dwFileFlagsMask,
      dwFileFlags, fileOS.toList, fileType, fileSubtype, dwFileDateMS, dwFileDateLS)
  }

}