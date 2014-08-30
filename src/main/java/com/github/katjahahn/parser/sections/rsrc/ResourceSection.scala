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
import java.io.File
import com.github.katjahahn.parser.sections.SectionLoader
import com.github.katjahahn.parser.sections.SpecialSection
import com.github.katjahahn.parser.PELoader
import com.github.katjahahn.parser.PEData
import com.github.katjahahn.parser.sections.SectionLoader.LoadInfo
import com.github.katjahahn.parser.MemoryMappedPE
import com.github.katjahahn.parser.Location
import scala.collection.mutable.ListBuffer
import com.github.katjahahn.parser.PhysicalLocation

/**
 * Holds the root resource directory table and provides access to the resources.
 *
 * @author Katja Hahn
 *
 * Creates an instance of the resource section with the resource
 * directory table
 *
 * @param resourceTable the root resource directory table that makes up the tree
 *   of the resource section
 * @param offset the file offset to the beginning of the resource table
 * @param mmBytes the memory mapped PE
 * @param hasLoop indicates whether the resource tree has a loop
 */
class ResourceSection private (
  val resourceTree: ResourceDirectory,
  private val offset: Long,
  private val mmBytes: MemoryMappedPE,
  val hasLoop: Boolean) extends SpecialSection {

  /**
   * Returns all file locations of the special section
   */
  def getLocations(): java.util.List[PhysicalLocation] =
    Location.mergeContinuous[PhysicalLocation](resourceTree.locations).toList.asJava

  /**
   * {@inheritDoc}
   */
  override def isEmpty(): Boolean = getResources.isEmpty()

  /**
   * {@inheritDoc}
   */
  override def getInfo(): String = resourceTree.getInfo

  /**
   * {@inheritDoc}
   */
  override def getOffset(): Long = offset

  /**
   * Returns the {@link ResourceDirectory} that is the root of the
   * resource tree
   *
   * @return the root node of the resource tree that makes up the resource section
   */
  def getResourceTree(): ResourceDirectory = resourceTree

  /**
   * Collects the resources from the root resource directory table and
   * returns them.
   *
   * @return a List of {@link Resource} instances
   */
  def getResources(): java.util.List[Resource] =
    resourceTree.getResources()

}

object ResourceSection {

  /**
   * Maximum depth for the resource tree that is read.
   */
  val maxLevel = 20;

  def main(args: Array[String]): Unit = {
    val file = new File("/home/deque/portextestfiles/unusualfiles/corkami/resource_loop.exe")
    val pedata = PELoader.loadPE(file)
    val rsrc = new SectionLoader(pedata).loadResourceSection()
    val res = rsrc.getResources.asScala
    println(res.mkString("\n"))
    println("nr of res: " + res.size)
  }

  /**
   * Creates an instance of the ResourceSection.
   *
   * @param file the PE file
   * @param virtualAddress the virtual address all RVAs are relative to
   * @param rsrcOffset the file offset to the rsrc section
   * @param mmBytes the memory mapped PE
   * @return instance of the resource section
   */
  def apply(file: File, virtualAddress: Long,
    rsrcOffset: Long, mmBytes: MemoryMappedPE): ResourceSection = {
    val initialLevel = Level()
    val initialOffset = 0
    val loopChecker = new ResourceLoopChecker()
    val resourceTable = ResourceDirectory(file, initialLevel,
      initialOffset, virtualAddress, rsrcOffset, mmBytes, loopChecker)
    val hasLoop = loopChecker.loopDetected
    new ResourceSection(resourceTable, rsrcOffset, mmBytes, hasLoop)
  }

  /**
   * Creates an instance of the ResourceSection.
   *
   * @param loadInfo the load information
   * @return instance of the resource section
   */
  def newInstance(loadInfo: LoadInfo): ResourceSection =
    apply(loadInfo.data.getFile, loadInfo.va, loadInfo.fileOffset, loadInfo.memoryMapped)

}

/**
 * Checks for resources loops. Exactly one instance per resource tree!
 */
class ResourceLoopChecker {
    /**
     * Saves references to known file offsets for resource directories to check for loops
     */
    private val fileOffsets = ListBuffer[Long]()
    private var _loopDetected: Boolean = false

    /**
     * Returns true if the node is a new node, false otherwise. Used to check for
     * resource tree loops.
     */
    def isNewResourceDirFileOffset(fileOffset: Long): Boolean = {
      val isNew = !fileOffsets.contains(fileOffset)
      fileOffsets += fileOffset
      if(!isNew) _loopDetected = true
      isNew
    }

    /**
     * Indicates if a loop was detected.
     */
    def loopDetected(): Boolean = _loopDetected
  }
