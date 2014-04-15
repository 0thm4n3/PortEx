/*******************************************************************************
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
 ******************************************************************************/
package com.github.katjahahn.sections;

import static com.github.katjahahn.sections.SectionTableEntryKey.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.katjahahn.PEData;
import com.github.katjahahn.optheader.DataDirEntry;
import com.github.katjahahn.optheader.DataDirectoryKey;
import com.github.katjahahn.optheader.OptionalHeader;
import com.github.katjahahn.sections.edata.ExportSection;
import com.github.katjahahn.sections.idata.ImportSection;
import com.github.katjahahn.sections.rsrc.ResourceSection;

/**
 * Responsible for computing section related values and loading sections with
 * the given section header information.
 * 
 * The section loader is a factory for special sections like the
 * {@link ImportSection}, {@link ExportSection} and {@link ResourceSection}
 * 
 * @author Katja Hahn
 * 
 */
public class SectionLoader {

	private static final Logger logger = LogManager
			.getLogger(SectionLoader.class.getName());

	private final SectionTable table;
	private final File file;
	private final OptionalHeader optHeader;

	/**
	 * @constructor Creates a SectionLoader instance with a file and the
	 *              corresponding section table and optional Header of that
	 *              file.
	 * 
	 * @param table
	 * @param optHeader
	 * @param file
	 */
	public SectionLoader(SectionTable table, OptionalHeader optHeader, File file) {
		this.table = table;
		this.file = file;
		this.optHeader = optHeader;
	}

	/**
	 * @constructor Creates a SectionLoader instance taking all information from
	 *              the given {@link PEData} object
	 * @param data
	 */
	public SectionLoader(PEData data) {
		this.table = data.getSectionTable();
		this.optHeader = data.getOptionalHeader();
		this.file = data.getFile();
	}

	/**
	 * Loads the section with the given name. If the file doesn't have a section
	 * by this name, it returns null.
	 * 
	 * This does not instantiate subclasses of {@link PESection}. Use methods
	 * like {@link #loadImportSection()} or {@link #loadResourceSection()}
	 * instead.
	 * 
	 * The file on disk is read to fetch the information
	 * 
	 * @param name
	 *            the section's name
	 * @return PESection of the given name, null if section isn't contained in
	 *         file
	 * @throws IOException
	 *             if unable to read the file
	 */
	public PESection loadSection(String name) throws IOException {
		return loadSection(name, false);
	}

	/**
	 * Loads the section with the given name and may patch the size of the
	 * section if the {@code patchSize} parameter is set. If the file doesn't
	 * have a section by this name, it returns null.
	 * 
	 * This does not instantiate subclasses of {@link PESection}. Use methods
	 * like {@link #loadImportSection()} or {@link #loadResourceSection()}
	 * instead.
	 * 
	 * The file on disk is read to fetch the information
	 * 
	 * @param name
	 *            the section's name
	 * @param patchSize
	 *            patches the section size if it surpasses the actual file size.
	 *            This is an anomaly and only useful while dealing with
	 *            corrupted PE files
	 * @return PESection of the given name, null if section isn't contained in
	 *         file
	 * @throws IOException
	 *             if unable to read the file
	 */
	public PESection loadSection(String name, boolean patchSize)
			throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			Long pointer = table.getPointerToRawData(name);
			if (pointer != null) {
				raf.seek(pointer);
				int sectionSize = getSectionSize(table.getSectionEntry(name),
						patchSize, pointer);
				byte[] sectionbytes = new byte[sectionSize];
				raf.readFully(sectionbytes);
				return new PESection(sectionbytes);
			}
		}
		return null;
	}

	/**
	 * Gets the section size and patches it if {@code patchSize} is set.
	 * 
	 * @param entry
	 *            section entry to get the size from
	 * @param patchSize
	 *            patches the section size if it surpasses the actual file size.
	 *            This is an anomaly and only useful while dealing with
	 *            corrupted PE files
	 * @param pointer
	 *            the pointertorawdata or file offset of the section
	 * @return a possibly patched section size
	 */
	private int getSectionSize(SectionTableEntry entry, boolean patchSize,
			Long pointer) {
		long sectionSize = entry.get(SectionTableEntryKey.SIZE_OF_RAW_DATA);
		long fileSize = file.length();
		if (patchSize && (pointer + sectionSize > fileSize)) {
			sectionSize = fileSize - pointer;
		}
		// TODO cast to int is insecure. actual int is unsigned,
		// java int is signed
		return (int) sectionSize;
	}

	/**
	 * Loads all bytes and information of the resource section.
	 * 
	 * The file on disk is read to fetch the information.
	 * 
	 * @return {@link ResourceSection} of the given file, null if file doesn't
	 *         have this section
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ResourceSection loadResourceSection() throws IOException {
		return loadResourceSection(false);
	}

	/**
	 * Loads all bytes and information of the resource section.
	 * 
	 * The file on disk is read to fetch the information.
	 * 
	 * @param patchSize
	 *            patches the given section size if it surpasses the actual file
	 *            size. This is an anomaly and only useful while dealing with
	 *            corrupted PE files
	 * @return {@link ResourceSection} of the given file, null if file doesn't
	 *         have this section
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ResourceSection loadResourceSection(boolean patchSize)
			throws IOException {
		DataDirEntry resourceTable = 
				optHeader.getDataDirEntries().get(DataDirectoryKey.RESOURCE_TABLE);
		if (resourceTable != null) {
			SectionTableEntry rsrcEntry = resourceTable
					.getSectionTableEntry(table);
			Long virtualAddress = rsrcEntry.get(VIRTUAL_ADDRESS);
			if (virtualAddress != null) {
				try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
					long pointer = rsrcEntry.get(POINTER_TO_RAW_DATA);
					raf.seek(pointer);
					int size = getSectionSize(rsrcEntry, patchSize, pointer);
					byte[] rsrcbytes = new byte[size];
					raf.readFully(rsrcbytes);
					ResourceSection rsrc = ResourceSection.getInstance(
							rsrcbytes, virtualAddress);
					return rsrc;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the section entry of the section table the rva is pointing into.
	 * 
	 * @param table
	 *            the section table of the file
	 * @param rva
	 *            the relative virtual address
	 * @return the {@link SectionTableEntry} of the section the rva is pointing
	 *         into
	 */
	public static SectionTableEntry getSectionByRVA(SectionTable table, long rva) {
		List<SectionTableEntry> sections = table.getSectionEntries();
		for (SectionTableEntry section : sections) {
			long vSize = section.get(VIRTUAL_SIZE);
			long vAddress = section.get(VIRTUAL_ADDRESS);
			if (rvaIsWithin(vAddress, vSize, rva)) {
				return section;
			}
		}
		return null;
	}

	private static boolean rvaIsWithin(long address, long size, long rva) {
		long endpoint = address + size;
		return rva >= address && rva < endpoint;
	}

	/**
	 * Loads all bytes and information of the import section. The file on disk
	 * is read to fetch the information.
	 * 
	 * @return the import section, null if file doesn't have an import
	 *         sectigetBytesLongValue(edataBytes, offset, length)on
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ImportSection loadImportSection() throws IOException {
		return loadImportSection(false);
	}

	/**
	 * Loads all bytes and information of the import section. The file on disk
	 * is read to fetch the information.
	 * 
	 * @param patchSize
	 *            patches the section size if it surpasses the actual file size.
	 *            This is an anomaly and only useful while dealing with
	 *            corrupted PE files
	 * @return the import section, null if file doesn't have an import
	 *         sectigetBytesLongValue(edataBytes, offset, length)on
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ImportSection loadImportSection(boolean patchSize)
			throws IOException {
		DataDirEntry importTable = 
				optHeader.getDataDirEntries().get(DataDirectoryKey.IMPORT_TABLE);
		if (importTable != null) {
			long virtualAddress = importTable.virtualAddress;
			byte[] idatabytes = readSectionBytesFor(
					DataDirectoryKey.IMPORT_TABLE, patchSize);
			int importTableOffset = getOffsetDiffFor(DataDirectoryKey.IMPORT_TABLE);
			ImportSection idata = ImportSection.getInstance(idatabytes,
					virtualAddress, optHeader, importTableOffset);
			idata.read();
			return idata;
		}
		return null;
	}

	/**
	 * Returns the difference between the offset the data directory entry and
	 * the begin of the section the entry is in.
	 * 
	 * E.g. the difference between file offset of the import table and the
	 * pointer_to_raw_data of the idata section the table is in.
	 * 
	 * @param dataDirKey
	 * @return the difference of the calculated data dir entry file offset to
	 *         the pointer_to_raw_data the data dir entry is in, null if no data
	 *         dir entry can be found for the specified key
	 */
	private Integer getOffsetDiffFor(DataDirectoryKey dataDirKey) {
		Long pointerToRawData = getSectionEntryValue(dataDirKey,
				POINTER_TO_RAW_DATA);
		Long offset = getFileOffsetFor(dataDirKey);
		if (pointerToRawData != null && offset != null) {
			return (int) (offset - pointerToRawData);
		}
		return null;
	}

	/**
	 * Fetches the {@link SectionTableEntry} of the section the data directory
	 * entry for the given key points into.
	 * 
	 * @param dataDirKey
	 *            the data directory key
	 * @return the section table entry the data directory entry of that key
	 *         points into
	 */
	private SectionTableEntry getSectionTableEntryFor(
			DataDirectoryKey dataDirKey) {
		DataDirEntry dataDir = 
				optHeader.getDataDirEntries().get(dataDirKey);
		if (dataDir != null) {
			return dataDir.getSectionTableEntry(table);
		}
		return null;
	}

	/**
	 * Retuns the value of the section entry the data directory entry of the
	 * given {@code dataDirkey} points into
	 * 
	 * @param dataDirKey
	 *            the key for the data directory entry that shall be used
	 * @param sectionKey
	 *            the key of the section entry value
	 * @return the section entry value that belongs to the given key
	 */
	private Long getSectionEntryValue(DataDirectoryKey dataDirKey,
			SectionTableEntryKey sectionKey) {
		SectionTableEntry section = getSectionTableEntryFor(dataDirKey);
		if (section != null) {
			return section.get(sectionKey);
		}
		return null;
	}

	/**
	 * Returns the file offset of the data directory entry the given key belongs
	 * to.
	 * 
	 * @param dataDirKey the key of the data directory entry
	 * @return file offset of the rva that is in the data directory entry with
	 *         the given key
	 */
	public Long getFileOffsetFor(DataDirectoryKey dataDirKey) {
		DataDirEntry dataDir = 
				optHeader.getDataDirEntries().get(dataDirKey);
		if (dataDir != null) {
			Long virtualAddress = getSectionEntryValue(dataDirKey,
					VIRTUAL_ADDRESS);
			Long pointerToRawData = getSectionEntryValue(dataDirKey,
					POINTER_TO_RAW_DATA);
			if (virtualAddress != null && pointerToRawData != null) {
				long rva = dataDir.virtualAddress;
				return rva - (virtualAddress - pointerToRawData);
			}
		}
		return null;
	}

	/**
	 * Returns all bytes of the section where the given data dir entry is in.
	 * 
	 * @param dataDirKey
	 * @param patchSize
	 * @return
	 * @throws IOException
	 */
	public byte[] readSectionBytesFor(DataDirectoryKey dataDirKey,
			boolean patchSize) throws IOException {
		DataDirEntry dataDir = 
				optHeader.getDataDirEntries().get(dataDirKey);
		if (dataDir != null) {
			Long virtualAddress = getSectionEntryValue(dataDirKey,
					VIRTUAL_ADDRESS);
			Long offset = getSectionEntryValue(dataDirKey, POINTER_TO_RAW_DATA);
			Long sizeOfRawData = getSectionEntryValue(dataDirKey,
					SIZE_OF_RAW_DATA);
			if (virtualAddress != null && offset != null
					&& sizeOfRawData != null) {
				long rva = dataDir.virtualAddress;
				if (patchSize && sizeOfRawData + offset > file.length()) {
					sizeOfRawData = file.length() - offset;
				}
				try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
					raf.seek(offset);
					virtualAddress = rva;
					// TODO cast to int is insecure. actual int is unsigned
					byte[] bytes = new byte[sizeOfRawData.intValue()];
					raf.readFully(bytes);
					return bytes;
				}
			}
		} else {
			logger.warn("invalid data dir key");
		}
		return null;
	}

	/**
	 * Loads all bytes and information of the export section. The file on disk
	 * is read to fetch the information.
	 * 
	 * @return the export section, null if file doesn't have an export section
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ExportSection loadExportSection() throws IOException {
		return loadExportSection(false);
	}

	/**
	 * Loads all bytes and information of the export section. The file on disk
	 * is read to fetch the information.
	 * 
	 * @param patchSize
	 *            patches the section size if it surpasses the actual file size.
	 *            This is an anomaly and only useful while dealing with
	 *            corrupted PE files
	 * @return the export section, null if file doesn't have an export section
	 * @throws IOException
	 *             if unable to read the file
	 */
	public ExportSection loadExportSection(boolean patchSize)
			throws IOException {
		DataDirEntry exportTable = 
				optHeader.getDataDirEntries().get(DataDirectoryKey.EXPORT_TABLE);
		if (exportTable != null) {
			long virtualAddress = exportTable.virtualAddress;
			byte[] edatabytes = readDataDirBytesFor(
					DataDirectoryKey.EXPORT_TABLE, patchSize);
			ExportSection edata = ExportSection.getInstance(edatabytes,
					virtualAddress, optHeader);
			return edata;
		}
		return null;
	}

	/**
	 * Reads and returns the bytes that belong to the given data directory
	 * entry.
	 * 
	 * The data directory entry rva points into section. This section is
	 * determined and the file offset for the rva calculated. This file offset
	 * is different from the beginning of the determined section, as the section
	 * may contain more than the data directory. The returned bytes start at
	 * that file offset and end at the end of the section the data directory is
	 * in.
	 * 
	 * @param dataDirKey
	 *            the key of the data directory entry you want the bytes for
	 * @return byte array that contains the bytes the data directory entry rva
	 *         is pointing to
	 * @throws IOException
	 *             if unable to read the file
	 */
	public byte[] readDataDirBytesFor(DataDirectoryKey dataDirKey,
			boolean patchSize) throws IOException {
		DataDirEntry dataDir = 
				optHeader.getDataDirEntries().get(dataDirKey);
		if (dataDir != null) {
			Long virtualAddress = getSectionEntryValue(dataDirKey,
					VIRTUAL_ADDRESS);
			Long pointerToRawData = getSectionEntryValue(dataDirKey,
					POINTER_TO_RAW_DATA);
			Long sizeOfRawData = getSectionEntryValue(dataDirKey,
					SIZE_OF_RAW_DATA);
			if (virtualAddress != null && pointerToRawData != null
					&& sizeOfRawData != null) {
				long rva = dataDir.virtualAddress;
				long offset = rva - (virtualAddress - pointerToRawData);
				long size = sizeOfRawData - (rva - virtualAddress);
				if (patchSize && sizeOfRawData + offset > file.length()) {
					size = file.length() - offset;
				}
				try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
					raf.seek(offset);
					virtualAddress = rva;
					// TODO cast to int is insecure. actual int is unsigned
					byte[] bytes = new byte[(int) size];
					raf.readFully(bytes);
					return bytes;
				}
			}
		} else {
			logger.warn("invalid dataDirKey");
		}
		return null;
	}

}
