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
package com.github.katjahahn.parser.sections;

import static com.github.katjahahn.parser.sections.SectionHeaderKey.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.katjahahn.parser.FileFormatException;
import com.github.katjahahn.parser.MemoryMappedPE;
import com.github.katjahahn.parser.PEData;
import com.github.katjahahn.parser.optheader.DataDirEntry;
import com.github.katjahahn.parser.optheader.DataDirectoryKey;
import com.github.katjahahn.parser.optheader.OptionalHeader;
import com.github.katjahahn.parser.sections.debug.DebugSection;
import com.github.katjahahn.parser.sections.edata.ExportSection;
import com.github.katjahahn.parser.sections.idata.ImportSection;
import com.github.katjahahn.parser.sections.pdata.ExceptionSection;
import com.github.katjahahn.parser.sections.rsrc.ResourceSection;
import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.java.contract.Ensures;
import com.google.java.contract.Invariant;
import com.google.java.contract.Requires;

/**
 * Responsible for computing section related values that are necessary for
 * loading a section---for example conversion between relative virtual addresses
 * and file offset---, loading data directories, and loading sections.
 * <p>
 * The section loader is able to load special sections like the
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
    private final PEData data;
    private Optional<MemoryMappedPE> memoryMapped = Optional.absent();

    /**
     * Creates a SectionLoader instance taking all information from the given
     * {@link PEData} object.
     * 
     * @param data
     */
    public SectionLoader(PEData data) {
        // extract data for easier access ;)
        this.table = data.getSectionTable();
        this.optHeader = data.getOptionalHeader();
        this.file = data.getFile();
        this.data = data;
    }

    /**
     * Loads the first section with the given name (given the order of the
     * section headers). If the file doesn't have a section by this name, it
     * returns absent.
     * <p>
     * This does not instantiate special sections. Use methods like
     * {@link #loadImportSection()} or {@link #loadResourceSection()} instead.
     * <p>
     * The file on disk is read to fetch the information.
     * 
     * @param name
     *            the section's name
     * @return {@link PESection} of the given name, absent if section isn't
     *         contained in file
     * @throws IOException
     *             if unable to read the file
     */
    @Ensures("result != null")
    @Beta
    // TODO remove? This seems not necessary
    public Optional<PESection> maybeLoadSection(String name) throws IOException {
        Optional<SectionHeader> section = table.getSectionHeaderByName(name);
        if (section.isPresent()) {
            int sectionNr = section.get().getNumber();
            return Optional.fromNullable(loadSection(sectionNr));
        }
        return Optional.absent();
    }

    /**
     * Loads the section that has the sectionNr.
     * <p>
     * This does not instantiate special sections. Use methods like
     * {@link #loadImportSection()} or {@link #loadResourceSection()} instead.
     * 
     * @param sectionNr
     *            the section's number
     * @return {@link PESection} instance of the given number
     * @throws IllegalArgumentException
     *             if invalid sectionNr
     */
    @Ensures("result != null")
    @Requires("sectionNr > 0")
    public PESection loadSection(int sectionNr) {
        Preconditions.checkArgument(
                sectionNr > 0 && sectionNr <= table.getNumberOfSections(),
                "invalid section number");
        SectionHeader header = table.getSectionHeader(sectionNr);
        return loadSectionFrom(header);
    }

    /**
     * Loads the section that belongs to the header.
     * <p>
     * This does not instantiate special sections. Use methods like
     * {@link #loadImportSection()} or {@link #loadResourceSection()} instead.
     * 
     * @param header
     *            the section's header
     * @return {@link PESection} that belongs to the header
     */
    @Ensures("result != null")
    public PESection loadSectionFrom(SectionHeader header) {
        long size = getReadSize(header);
        long offset = header.getAlignedPointerToRaw();
        return new PESection(size, offset, header, file);
    }

    /**
     * Loads the bytes of the section and returns bytes and file offset.
     * 
     * @param header
     *            of the section
     * @return bytes and file offset of the section
     * @throws IOException
     *             if file can not be read
     * @throws IllegalStateException
     *             if section too large to fit into a byte array
     */
    @Requires("header != null")
    @Ensures("result != null")
    @Beta
    // TODO maybe remove
    private BytesAndOffset loadSectionBytesFor(SectionHeader header)
            throws IOException {
        Preconditions.checkArgument(header != null, "given section was null");
        logger.debug("reading section bytes for header " + header.getName());
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long alignedPointerToRaw = header.getAlignedPointerToRaw();
            long readSize = getReadSize(header);
            Preconditions.checkState(readSize == (int) readSize,
                    "read size too large to load section into byte array");
            raf.seek(alignedPointerToRaw);
            logger.debug("reading section bytes from " + alignedPointerToRaw
                    + " to " + (readSize + alignedPointerToRaw));
            byte[] sectionbytes = new byte[(int) readSize];
            raf.readFully(sectionbytes);
            return new BytesAndOffset(sectionbytes, alignedPointerToRaw);
        }
    }

    /**
     * Rounds up the value to the file alignment of the optional header.
     * 
     * @param value
     * @return file aligned value
     */
    @Ensures({ "optHeader.isLowAlignmentMode() || result % 512 == 0",
            "result >= value" })
    private long fileAligned(long value) {
        long fileAlign = optHeader.getAdjustedFileAlignment();
        long rest = value % fileAlign;
        long result = value;
        if (rest != 0) {
            result = value - rest + fileAlign;
        }
        if (!(optHeader.isLowAlignmentMode() || result % 512 == 0)) {
            logger.error("file aligning went wrong");
            logger.error("value: " + value);
            logger.error("filealign: " + fileAlign);
            logger.error("result: " + result);
        }
        return result;
    }

    /**
     * Determines the the number of bytes that is read for the section.
     * <p>
     * This is the actual section size.
     * 
     * @param header
     *            header of the section
     * @return section size
     * @throws IllegalArgumentException
     *             if header is null
     */
    @Ensures("result >= 0")
    @Requires("header != null")
    public long getReadSize(SectionHeader header) {
        Preconditions.checkArgument(header != null);
        long pointerToRaw = header.get(POINTER_TO_RAW_DATA);
        long virtSize = header.get(VIRTUAL_SIZE);
        long sizeOfRaw = header.get(SIZE_OF_RAW_DATA);
        long alignedPointerToRaw = header.getAlignedPointerToRaw();
        // see Peter Ferrie's answer in:
        // https://reverseengineering.stackexchange.com/questions/4324/reliable-algorithm-to-extract-overlay-of-a-pe
        long readSize = fileAligned(pointerToRaw + sizeOfRaw)
                - alignedPointerToRaw;
        readSize = Math.min(readSize, header.getAlignedSizeOfRaw());
        // see https://code.google.com/p/corkami/wiki/PE#section_table:
        // "if bigger than virtual size, then virtual size is taken. "
        // and:
        // "a section can have a null VirtualSize: in this case, only the SizeOfRawData is taken into consideration. "
        if (virtSize != 0) {
            readSize = Math.min(readSize, header.getAlignedVirtualSize());
        }
        readSize = fileSizeAdjusted(alignedPointerToRaw, readSize);
        // must not happen
        if (readSize < 0) {
            logger.error("Invalid readsize: " + readSize + " for file "
                    + file.getName() + " adjusting readsize to 0");
            readSize = 0;
        }
        return readSize;
    }

    /**
     * Adjusts the readsize of a section to the size of the file.
     * 
     * @param alignedPointerToRaw
     *            the file offset of the start of the section
     * @param readSize
     *            the determined readsize without file adjustments
     * @return adjusted readsize
     */
    private long fileSizeAdjusted(long alignedPointerToRaw, long readSize) {
        // end of section outside the file --> cut at file.length()
        if (readSize + alignedPointerToRaw > file.length()) {
            readSize = file.length() - alignedPointerToRaw;
        }
        // start of section outside the file --> nothing is read
        if (alignedPointerToRaw > file.length()) {
            logger.info("invalid section: starts outside the file, readsize set to 0");
            readSize = 0;
        }
        return readSize;
    }

    /**
     * Fetches the {@link SectionHeader} of the section the data directory entry
     * for the dataDirKey points into.
     * <p>
     * Returns absent if the data directory doesn't exist.
     * 
     * @param dataDirKey
     *            the data directory key
     * @return the section table entry the data directory entry of that key
     *         points into or absent if there is no data dir entry for the key
     *         available
     */
    private Optional<SectionHeader> maybeGetSectionHeader(
            DataDirectoryKey dataDirKey) {
        Optional<DataDirEntry> dataDir = optHeader
                .maybeGetDataDirEntry(dataDirKey);
        if (dataDir.isPresent()) {
            return dataDir.get().maybeGetSectionTableEntry(table);
        }
        logger.warn("data dir entry " + dataDirKey + " doesn't exist");
        return Optional.absent();
    }

    /**
     * Returns the file offset of the data directory entry the given key belongs
     * to.
     * <p>
     * Returns absent if data directory doesn't exist.
     * 
     * @param dataDirKey
     *            the key of the data directory entry
     * @return file offset of the rva that is in the data directory entry with
     *         the given key, absent if file offset can not be determined
     */
    @Ensures("result != null")
    public Optional<Long> maybeGetFileOffsetFor(DataDirectoryKey dataDirKey) {
        Optional<DataDirEntry> dataDir = optHeader
                .maybeGetDataDirEntry(dataDirKey);
        if (dataDir.isPresent()) {
            long rva = dataDir.get().virtualAddress;
            return maybeGetFileOffset(rva);
        }
        return Optional.absent();
    }

    /**
     * Returns the file offset for the given RVA.
     * <p>
     * Returns absent if determined offset would point outside the file.
     * 
     * @param rva
     *            the relative virtual address that shall be converted to a
     *            plain file offset
     * @return file offset optional, absent if file offset can not be
     *         determined.
     */
    @Ensures("result != null")
    public Optional<Long> maybeGetFileOffset(long rva) {
        Optional<SectionHeader> section = maybeGetSectionHeaderByRVA(rva);
        // standard value if rva doesn't point into a section
        long fileOffset = rva;
        // rva is located within a section
        if (section.isPresent()) {
            long virtualAddress = section.get().get(VIRTUAL_ADDRESS);
            long pointerToRawData = section.get().get(POINTER_TO_RAW_DATA);
            fileOffset = rva - (virtualAddress - pointerToRawData);
        }
        // file offset is valid
        if (rva <= file.length()) {
            return Optional.of(fileOffset);
        }
        // file offset is invalid
        return Optional.absent();
    }

    /**
     * Returns the section entry of the section table the rva is pointing into.
     * 
     * @param table
     *            the section table of the file
     * @param rva
     *            the relative virtual address
     * @return the {@link SectionHeader} of the section the rva is pointing into
     */
    @Ensures("result != null")
    public Optional<SectionHeader> maybeGetSectionHeaderByRVA(long rva) {
        List<SectionHeader> sections = table.getSectionHeaders();
        for (SectionHeader section : sections) {
            long vSize = section.get(VIRTUAL_SIZE);
            long vAddress = section.get(VIRTUAL_ADDRESS);
            if (rvaIsWithin(vAddress, vSize, rva)) {
                return Optional.of(section);
            }
        }
        return Optional.absent();
    }

    /**
     * Returns true if rva is within address and size of a section
     * 
     * @param address
     *            start of a section
     * @param size
     *            size of a section
     * @param rva
     *            a relative virtual address that may point into the section
     * @return true iff rva is within section range
     */
    private static boolean rvaIsWithin(long address, long size, long rva) {
        long endpoint = address + size;
        return rva >= address && rva < endpoint;
    }

    /**
     * Loads all bytes and information of the debug section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link DebugSection} of the given file
     * @throws IOException
     *             if unable to read the file
     * @throws IllegalStateException
     *             if unable to load debug section
     */
    @Ensures("result != null")
    public DebugSection loadDebugSection() throws IOException {
        Optional<DebugSection> debug = maybeLoadDebugSection();
        if (debug.isPresent()) {
            return debug.get();
        }
        throw new IllegalStateException("unable to load debug section");
    }

    /**
     * Loads all bytes and information of the debug section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link DebugSection} of the given file, absent if file doesn't
     *         have this section
     * @throws {@link IOException} if unable to read the file
     */
    @Ensures("result != null")
    public Optional<DebugSection> maybeLoadDebugSection() throws IOException {
        DataDirectoryKey debugKey = DataDirectoryKey.DEBUG;
        Optional<LoadInfo> loadInfo = maybeGetLoadInfo(debugKey);
        if (loadInfo.isPresent()) {
            DebugSection sec = DebugSection.newInstance(loadInfo.get());
            if (sec.isEmpty()) {
                logger.warn("empty debug section");
            }
            return Optional.of(sec);
        }
        return Optional.absent();
    }

    /**
     * Loads all bytes and information of the resource section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link ResourceSection} of the given file
     * @throws {@link IOException} if unable to read the file
     * @throws @{@link IllegalStateException} if section can not be loaded
     */
    @Ensures("result != null")
    public ResourceSection loadResourceSection() throws IOException,
            FileFormatException {
        Optional<ResourceSection> rsrc = maybeLoadResourceSection();
        if (rsrc.isPresent()) {
            return rsrc.get();
        }
        throw new IllegalStateException("unable to load resource section");
    }

    /**
     * Loads all bytes and information of the resource section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link ResourceSection} of the given file, absent if file doesn't
     *         have this section
     * @throws IOException
     *             if unable to read the file
     */
    @Ensures("result != null")
    //TODO use MemoryMappedPE and loadInfo
    public Optional<ResourceSection> maybeLoadResourceSection()
            throws IOException, FileFormatException {
        Optional<DataDirEntry> resourceTable = optHeader
                .maybeGetDataDirEntry(DataDirectoryKey.RESOURCE_TABLE);
        if (resourceTable.isPresent()) {
            Optional<SectionHeader> rsrcEntry = resourceTable.get()
                    .maybeGetSectionTableEntry(table);
            if (rsrcEntry.isPresent()) {
                Long virtualAddress = rsrcEntry.get().get(VIRTUAL_ADDRESS);
                if (virtualAddress != null) {
                    BytesAndOffset tuple = loadSectionBytesFor(rsrcEntry.get());
                    byte[] rsrcbytes = tuple.bytes;
                    if (rsrcbytes.length == 0) {
                        logger.warn("unable to read resource section, readsize is 0");
                        return Optional.absent();
                    }
                    long rsrcOffset = rsrcEntry.get().getAlignedPointerToRaw();
                    ResourceSection rsrc = ResourceSection.newInstance(file,
                            rsrcbytes, virtualAddress, rsrcOffset);
                    return Optional.of(rsrc);
                }
            }
        }
        return Optional.absent();
    }

    /**
     * Loads all bytes and information of the exception section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link ExceptionSection} of the given file
     * @throws {@link IOException} if unable to read the file
     * @throws @{@link IllegalStateException} if section can not be loaded
     */
    @Ensures("result != null")
    public ExceptionSection loadExceptionSection() throws IOException {
        Optional<ExceptionSection> pdata = maybeLoadExceptionSection();
        if (pdata.isPresent()) {
            return pdata.get();
        }
        throw new IllegalStateException("unable to load exception section");
    }

    /**
     * Loads all bytes and information of the resource section.
     * 
     * The file on disk is read to fetch the information.
     * 
     * @return {@link ResourceSection} of the given file, absent if file doesn't
     *         have this section
     * @throws IOException
     *             if unable to read the file
     */
    @Ensures("result != null")
    public Optional<ExceptionSection> maybeLoadExceptionSection()
            throws IOException {
        DataDirectoryKey dataDirKey = DataDirectoryKey.EXCEPTION_TABLE;
        Optional<LoadInfo> loadInfo = maybeGetLoadInfo(dataDirKey);
        if (loadInfo.isPresent()) {
            ExceptionSection pdata = ExceptionSection.newInstance(loadInfo.get());
            if (pdata.isEmpty()) {
                logger.warn("empty exception section");
            }
            return Optional.of(pdata);
        }
        return Optional.absent();
    }

    /**
     * Loads all bytes and information of the import section. The file on disk
     * is read to fetch the information.
     * 
     * @return the import section
     * @throws {@link IOException} if unable to read the file
     * @throws {@link IllegalStateException} if unable to load section
     */
    @Ensures("result != null")
    public ImportSection loadImportSection() throws IOException {
        Optional<ImportSection> idata = maybeLoadImportSection();
        if (idata.isPresent()) {
            return idata.get();
        }
        throw new IllegalStateException("unable to load import section");
    }

    /**
     * Loads all bytes and information of the import section. The file on disk
     * is read to fetch the information.
     * 
     * @return the import section, absent if section can not be loaded
     * @throws {@link IOException} if unable to read the file
     */
    @Ensures("result != null")
    public Optional<ImportSection> maybeLoadImportSection() throws IOException {
        DataDirectoryKey dataDirKey = DataDirectoryKey.IMPORT_TABLE;
        Optional<LoadInfo> loadInfo = maybeGetLoadInfo(dataDirKey);
        if (loadInfo.isPresent()) {
            ImportSection idata = ImportSection.newInstance(loadInfo.get());
            if (idata.isEmpty()) {
                logger.warn("empty import section");
            }
            return Optional.of(idata);
        }
        return Optional.absent();
    }

    /**
     * Loads all bytes and information of the export section. The file on disk
     * is read to fetch the information.
     * 
     * @return the export section
     * @throws IOException
     *             if unable to read the file
     * @throws IllegalStateException
     *             if unable to load section
     */
    @Ensures("result != null")
    public ExportSection loadExportSection() throws IOException {
        Optional<ExportSection> edata = maybeLoadExportSection();
        if (edata.isPresent()) {
            return edata.get();
        }
        throw new IllegalStateException("unable to load export section");
    }

    /**
     * Loads all bytes and information of the export section. The file on disk
     * is read to fetch the information.
     * 
     * @return the export section, absent if file doesn't have an export section
     * @throws IOException
     *             if unable to read the file
     */
    @Ensures("result != null")
    public Optional<ExportSection> maybeLoadExportSection() throws IOException {
        Optional<LoadInfo> loadInfo = maybeGetLoadInfo(DataDirectoryKey.EXPORT_TABLE);
        if (loadInfo.isPresent()) {
            ExportSection edata = ExportSection.newInstance(loadInfo.get());
            if (edata.isEmpty()) {
                logger.warn("empty export section");
            }
            return Optional.of(edata);
        }
        return Optional.absent();
    }

    private MemoryMappedPE getMemoryMappedPE() {
        if (!memoryMapped.isPresent()) {
            memoryMapped = Optional.of(MemoryMappedPE.newInstance(data, this));
        }
        return memoryMapped.get();
    }

    private Optional<LoadInfo> maybeGetLoadInfo(DataDirectoryKey dataDirKey) {
        Optional<DataDirEntry> dirEntry = optHeader
                .maybeGetDataDirEntry(dataDirKey);
        if (dirEntry.isPresent()) {
            long virtualAddress = dirEntry.get().virtualAddress;
            Optional<Long> maybeOffset = maybeGetFileOffsetFor(dataDirKey);
            long offset = maybeOffset.or(0L);
            return Optional.of(new LoadInfo(offset, virtualAddress,
                    getMemoryMappedPE(), data, this));
        }
        return Optional.absent();
    }

    /**
     * 
     * Data structure to return or pass a bytes and offset pair.
     * 
     * @author Katja Hahn
     * 
     */
    @Invariant({ "bytes != null", "offset >= 0" })
    @Beta
    // TODO remove
    public static class BytesAndOffset {
        public final long offset;
        public final byte[] bytes;

        public BytesAndOffset(byte[] bytes, long offset) {
            Preconditions.checkArgument(bytes != null, "bytes are null");
            this.offset = offset;
            this.bytes = bytes;
        }
    }

    public static class LoadInfo {

        public final long fileOffset;
        public final long rva;
        public final PEData data;
        public final MemoryMappedPE memoryMapped;
        public final SectionLoader loader;

        public LoadInfo(long fileOffset, long rva, MemoryMappedPE memoryMapped,
                PEData data, SectionLoader loader) {
            this.fileOffset = fileOffset;
            this.rva = rva;
            this.memoryMapped = memoryMapped;
            this.data = data;
            this.loader = loader;
        }

    }

    /**
     * Returns whether the data directory entry points into a valid section.
     * <p>
     * Returns false if no data directory entry is present for the key or the
     * section is invalid.
     * 
     * @see #isValidSection()
     * @param dataDirKey
     *            the key for the data directory entry
     * @return true if the data directory entry for the key points into a valid
     *         section. False if no data directory entry present for the key or
     *         section invalid.
     */
    public boolean pointsToValidSection(DataDirectoryKey dataDirKey) {
        Optional<SectionHeader> header = maybeGetSectionHeader(dataDirKey);
        if (header.isPresent()) {
            return isValidSection(header.get());
        }
        return false;
    }

    /**
     * Returns whether the section is valid.
     * <p>
     * A section is valid if the readsize is greater than 0 and the section
     * start is within the file.
     * 
     * @see #getReadSize()
     * @param header
     *            the section's header
     * @return true iff section is valid
     */
    public boolean isValidSection(SectionHeader header) {
        // sidenote: the readsize should never be > 0 if the section starts
        // outside the file
        // but we make sure that everything is alright
        return getReadSize(header) > 0
                && header.getAlignedPointerToRaw() < file.length();
    }

}
