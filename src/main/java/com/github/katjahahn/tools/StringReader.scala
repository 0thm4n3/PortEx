package com.github.katjahahn.tools

import java.io.File
import java.io.ByteArrayInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import java.io.InputStream

/**
 * @author Katja Hahn
 *
 * <pre>
 * {@code
 * readStrings(new File("path"), 3);
 * }
 * </pre>
 */
object StringReader {

  def main(args: Array[String]): Unit = {
    println(readStrings(new File("WinRar.exe"), 4).asScala.mkString("\n"))
  }

  /**
   * Reads all 2-byte (Unicode) and 1-byte (ASCII) based character-strings
   * contained in the file. Only printable ASCII characters are determined as string.
   *
   * @param file the file that is to be searched for strings
   * @param minLength the minimum length the strings shall have
   * @return List containing the Strings found
   */
  def readStrings(file: File, minLength: Int): java.util.List[String] = {
    (_readASCIIStrings(file, minLength) ::: _readUnicodeStrings(file, minLength)).asJava
  }

   /**
   * Reads all 1-byte (ASCII) based character-strings
   * contained in the file. Only printable ASCII characters are determined as string.
   *
   * @param file the file that is to be searched for strings
   * @param minLength the minimum length the strings shall have
   * @return List containing the Strings found
   */
  def readASCIIStrings(file: File, minLength: Int): java.util.List[String] = {
    _readASCIIStrings(file, minLength).asJava
  }

  def _readASCIIStrings(file: File, minLength: Int): List[String] = {
    val strings = new ListBuffer[String]
    using(new FileInputStream(file)) { is =>
      var byte: Int = is.read()
      while (byte != -1) {
        byte = dropWhile(is, !isASCIIPrintable(_))
        if (byte != -1) {
          val (taken, b) = takeWhile(is, isASCIIPrintable(_))
          if (taken.length() > minLength) {
            strings.append(byte.toChar + taken)
          }
          byte = b
        }
      }
    }
    strings.toList
  }

  private def isASCIIPrintable(i: Int) = i >= 32 && i < 127

  private def takeWhile(is: InputStream, f: Int => Boolean): (String, Int) = {
    var byte: Int = is.read()
    val str = new StringBuffer()
    while (byte != -1 && f(byte)) {
      str.append(byte.toChar)
      byte = is.read();
    }
    (str.toString(), byte)
  }

  private def dropWhile(is: InputStream, f: Int => Boolean): Int = {
    var byte: Int = is.read()
    while (byte != -1 && f(byte)) {
      byte = is.read()
    }
    byte
  }

  private def using[A <: { def close(): Unit }, B](param: A)(f: A => B): B =
    try { f(param) } finally { param.close() }

 /**
   * Reads all 2-byte (Unicode) based character-strings
   * contained in the file. Only printable ASCII characters are determined as string.
   *
   * @param file the file that is to be searched for strings
   * @param minLength the minimum length the strings shall have
   * @return List containing the Strings found
   */
  def readUnicodeStrings(file: File, minLength: Int): java.util.List[String] = {
    _readUnicodeStrings(file, minLength).asJava
  }

  def _readUnicodeStrings(file: File, minLength: Int): List[String] = {
    val strings = new ListBuffer[String]
    var str = new StringBuilder()
    using(new FileInputStream(file)) { is =>
      var prev: Int = is.read()
      if (prev == -1) return strings.toList
      var byte: Int = is.read()
      var lastWasASCII = false

      while (prev != -1 && byte != -1) {
        val c = (prev << 8) + byte
        if (isASCIIPrintable(c)) {
          str.append(c)
        } else if (lastWasASCII) {
          if (str.length > minLength) {
            strings.append(str.toString)
          }
          str = new StringBuilder()
        } 
        lastWasASCII = isASCIIPrintable(c)
        prev = is.read()
        if (prev != -1) byte = is.read()
      }

    }
    strings.toList
  }
}