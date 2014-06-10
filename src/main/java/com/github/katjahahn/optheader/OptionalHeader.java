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
package com.github.katjahahn.optheader;

import static com.github.katjahahn.ByteArrayUtil.*;
import static com.github.katjahahn.optheader.StandardFieldEntryKey.*;
import static com.github.katjahahn.optheader.WindowsEntryKey.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.katjahahn.Header;
import com.github.katjahahn.HeaderKey;
import com.github.katjahahn.IOUtil;
import com.github.katjahahn.StandardField;
import com.google.common.base.Optional;
import com.google.java.contract.Ensures;

/**
 * Represents the optional header of the PE file.
 * 
 * @author Katja Hahn
 * 
 */
public class OptionalHeader extends Header<OptionalHeaderKey> {

    private static final Logger logger = LogManager
            .getLogger(OptionalHeader.class.getName());

    /* spec locations */
    private static final String SUBSYSTEM_SPEC = "subsystem";
    private static final String DLL_CHARACTERISTICS_SPEC = "dllcharacteristics";
    private static final String STANDARD_SPEC = "optionalheaderstandardspec";
    private static final String WINDOWS_SPEC = "optionalheaderwinspec";
    private static final String DATA_DIR_SPEC = "datadirectoriesspec";
    /**
     * Maximum size of the optional header to read all values safely is {@value}
     */
    public static final int MAX_SIZE = 240;
    // minimum size of the optional header with magic number taken into account
    private int minSize;
    // maximum size estimated for NrOfRVAAndValue = 16
    private int maxSize;

    /* extracted file data */
    private Map<DataDirectoryKey, DataDirEntry> dataDirEntries;
    private Map<StandardFieldEntryKey, StandardField> standardFields;
    private Map<WindowsEntryKey, StandardField> windowsFields;

    private final byte[] headerbytes;
    private MagicNumber magicNumber;
    private int rvaNumber;
    private final long offset;

    /**
     * The magic number of the PE file, indicating whether it is a PE32 or PE32+
     * 
     * @author Katja Hahn
     * 
     */
    public static enum MagicNumber {
        PE32(0x10B), PE32_PLUS(0x20B), ROM(0x107);

        private int value;

        private MagicNumber(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Creates an optional header instance with the given headerbytes and the
     * file offset of the beginning of the header
     * 
     * @param headerbytes
     * @param offset
     */
    public OptionalHeader(byte[] headerbytes, long offset) {
        this.headerbytes = headerbytes.clone();
        this.offset = offset;
    }

    @Override
    public void read() throws IOException {
        Map<String, String[]> standardSpec = IOUtil.readMap(STANDARD_SPEC);
        Map<String, String[]> windowsSpec = IOUtil.readMap(WINDOWS_SPEC);
        List<String[]> datadirSpec = IOUtil.readArray(DATA_DIR_SPEC);

        this.magicNumber = readMagicNumber(standardSpec);

        if (magicNumber.equals(MagicNumber.PE32)) {
            minSize = 100;
            maxSize = 224;
        } else {
            standardSpec.remove("BASE_OF_DATA");
            minSize = 112;
            maxSize = 240;
        }

        loadStandardFields(standardSpec);
        loadWindowsSpecificFields(windowsSpec);
        loadDataDirectories(datadirSpec);
    }

    /**
     * Returns a map of the data directory entries with the
     * {@link DataDirectoryKey} as key
     * 
     * @return the data directory entries
     */
    @Ensures("result != null")
    public Map<DataDirectoryKey, DataDirEntry> getDataDirEntries() {
        return new HashMap<>(dataDirEntries);
    }

    /**
     * Returns a map of the windows specific fields with the
     * {@link WindowsEntryKey} as key type
     * 
     * @return the windows specific fields
     */
    @Ensures("result != null")
    public Map<WindowsEntryKey, StandardField> getWindowsSpecificFields() {
        return new HashMap<>(windowsFields);
    }

    /**
     * Returns a map of the standard fields.
     * 
     * @return the standard fields
     */
    @Ensures("result != null")
    public Map<StandardFieldEntryKey, StandardField> getStandardFields() {
        return new HashMap<>(standardFields);
    }

    /**
     * Returns the optional data directory entry for the given key or absent if
     * entry doesn't exist.
     * 
     * @param key
     * @return the data directory entry for the given key or absent if entry
     *         doesn't exist.
     */
    @Ensures("result != null")
    public Optional<DataDirEntry> getDataDirEntry(DataDirectoryKey key) {
        return Optional.fromNullable(dataDirEntries.get(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get(OptionalHeaderKey key) {
        return getField(key).value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardField getField(OptionalHeaderKey key) {
        if (key instanceof StandardFieldEntryKey) {
            return standardFields.get(key);

        }
        return windowsFields.get(key);
    }

    /**
     * Returns the standard field entry for the given key.
     * 
     * @param key
     * @return the standard field entry for the given key
     */
    @Ensures("result != null")
    public StandardField getStandardFieldEntry(StandardFieldEntryKey key) {
        return standardFields.get(key);
    }

    /**
     * Returns the windows field entry for the given key.
     * 
     * @param key
     * @return the windows field entry for the given key
     */
    @Ensures("result != null")
    public StandardField getWindowsFieldEntry(WindowsEntryKey key) {
        return windowsFields.get(key);
    }

    private void loadStandardFields(Map<String, String[]> standardSpec) {
        standardFields = initStandardFields();
        int description = 0;
        int offsetLoc = 1;
        int lengthLoc = 2;

        for (Entry<String, String[]> entry : standardSpec.entrySet()) {
            String[] specs = entry.getValue();
            int offset = Integer.parseInt(specs[offsetLoc]);
            int length = Integer.parseInt(specs[lengthLoc]);
            if (headerbytes.length >= offset + length) {
                long value = getBytesLongValue(headerbytes, offset, length);
                StandardFieldEntryKey key = StandardFieldEntryKey.valueOf(entry
                        .getKey());
                standardFields.put(key, new StandardField(key,
                        specs[description], value));
            }
        }

    }

    @Ensures({ "result != null",
            "result.size() == StandardFieldEntryKey.values().length" })
    private Map<StandardFieldEntryKey, StandardField> initStandardFields() {
        Map<StandardFieldEntryKey, StandardField> map = new HashMap<>();
        for (StandardFieldEntryKey key : StandardFieldEntryKey.values()) {
            map.put(key, new StandardField(key, "absent", 0L));
        }
        return map;
    }

    private void loadDataDirectories(List<String[]> datadirSpec) {
        dataDirEntries = new HashMap<>();
        final int description = 0;
        int offsetLoc;
        int length = 4; // the actual length

        if (magicNumber == MagicNumber.PE32) {
            offsetLoc = 1;
        } else if (magicNumber == MagicNumber.PE32_PLUS) {
            offsetLoc = 2;
        } else {
            return; // no fields
        }

        int counter = 0;
        for (String[] specs : datadirSpec) {
            if (counter >= rvaNumber) {
                break;
            }
            int offset = Integer.parseInt(specs[offsetLoc]);
            if (headerbytes.length >= offset) {
                long address = getBytesLongValueSafely(headerbytes, offset,
                        length);
                long size = getBytesLongValueSafely(headerbytes, offset
                        + length, length);
                if (address != 0) {
                    DataDirEntry entry = new DataDirEntry(specs[description],
                            address, size);
                    dataDirEntries.put(entry.key, entry);
                }
            }
            counter++;
        }
    }

    private void loadWindowsSpecificFields(Map<String, String[]> windowsSpec) {
        windowsFields = initWindowsFields();
        int offsetLoc;
        int lengthLoc;
        final int description = 0;

        if (magicNumber == MagicNumber.PE32) {
            offsetLoc = 1;
            lengthLoc = 3;
        } else if (magicNumber == MagicNumber.PE32_PLUS) {
            offsetLoc = 2;
            lengthLoc = 4;
        } else {
            return; // no fields
        }

        for (Entry<String, String[]> entry : windowsSpec.entrySet()) {
            String[] specs = entry.getValue();
            int offset = Integer.parseInt(specs[offsetLoc]);
            int length = Integer.parseInt(specs[lengthLoc]);
            if (headerbytes.length >= offset + length) {
                long value = getBytesLongValue(headerbytes, offset, length);
                WindowsEntryKey key = WindowsEntryKey.valueOf(entry.getKey());
                windowsFields.put(key, new StandardField(key,
                        specs[description], value));
                if (key.equals(NUMBER_OF_RVA_AND_SIZES)) {
                    this.rvaNumber = (int) value; // always 4 Bytes
                    if (rvaNumber > 16) {
                        rvaNumber = 16;
                    }
                }
            }
        }
    }

    @Ensures({ "result != null",
            "result.size() == WindowsEntryKey.values().length" })
    private Map<WindowsEntryKey, StandardField> initWindowsFields() {
        Map<WindowsEntryKey, StandardField> map = new HashMap<>();
        for (WindowsEntryKey key : WindowsEntryKey.values()) {
            map.put(key, new StandardField(key, "absent", 0L));
        }
        return map;
    }

    @Override
    public String getInfo() {
        return "---------------" + NL + "Optional Header" + NL
                + "---------------" + NL + NL + "Standard fields" + NL
                + "..............." + NL + NL + getStandardFieldsInfo() + NL
                + "Windows specific fields" + NL + "......................."
                + NL + NL + getWindowsSpecificInfo() + NL + "Data directories"
                + NL + "................" + NL + NL + "virtual_address/size"
                + NL + NL + getDataDirInfo();
    }

    /**
     * Returns a description of all data directories.
     * 
     * @return description of all data directories.
     */
    public String getDataDirInfo() {
        StringBuilder b = new StringBuilder();
        for (DataDirEntry entry : dataDirEntries.values()) {
            b.append(entry.key + ": " + entry.virtualAddress + "(0x"
                    + Long.toHexString(entry.virtualAddress) + ")/"
                    + entry.size + "(0x" + Long.toHexString(entry.size) + ")"
                    + NL);
        }
        return b.toString();
    }

    /**
     * Returns a string with description of the windows specific header fields.
     * Magic number must be set.
     * 
     * @return string with windows specific fields
     */
    public String getWindowsSpecificInfo() {
        StringBuilder b = new StringBuilder();
        for (StandardField entry : windowsFields.values()) {
            long value = entry.value;
            HeaderKey key = entry.key;
            String description = entry.description;
            if (key.equals(IMAGE_BASE)) {
                b.append(description + ": " + value + " (0x"
                        + Long.toHexString(value) + "), "
                        + getImageBaseDescription(value) + NL);
            } else if (key.equals(SUBSYSTEM)) {
                b.append(description + ": "
                        + getSubsystemDescription((int) value) + NL); // subsystem
                                                                      // has
                                                                      // only
                                                                      // 2
                                                                      // Bytes
            } else if (key.equals(DLL_CHARACTERISTICS)) {
                b.append(NL + description + ": " + NL);
                b.append(IOUtil.getCharacteristics(value,
                        DLL_CHARACTERISTICS_SPEC) + NL);
            }

            else {
                b.append(description + ": " + value + " (0x"
                        + Long.toHexString(value) + ")" + NL);
                if (key.equals(NUMBER_OF_RVA_AND_SIZES)) {
                    rvaNumber = (int) value; // rva nr has always 4 Bytes
                }
            }
        }
        return b.toString();
    }

    /**
     * 
     * @return a description of all standard fields
     */
    public String getStandardFieldsInfo() {
        StringBuilder b = new StringBuilder();
        for (StandardField entry : standardFields.values()) {
            long value = entry.value;
            HeaderKey key = entry.key;
            String description = entry.description;
            if (key.equals(MAGIC_NUMBER)) {
                b.append(description + ": " + value + " --> "
                        + getMagicNumberString(magicNumber) + NL);
            } else if (key.equals(BASE_OF_DATA)) {
                if (magicNumber == MagicNumber.PE32) {
                    b.append(description + ": " + value + " (0x"
                            + Long.toHexString(value) + ")" + NL);
                }
            } else {
                b.append(description + ": " + value + " (0x"
                        + Long.toHexString(value) + ")" + NL);
            }
        }
        return b.toString();
    }

    private MagicNumber readMagicNumber(Map<String, String[]> standardSpec) {
        int offset = Integer.parseInt(standardSpec.get("MAGIC_NUMBER")[1]);
        int length = Integer.parseInt(standardSpec.get("MAGIC_NUMBER")[2]);
        long value = getBytesLongValue(headerbytes, offset, length);
        for (MagicNumber num : MagicNumber.values()) {
            if (num.getValue() == value) {
                return num;
            }
        }
        throw new IllegalArgumentException("unable to read magic number");
    }

    /**
     * Returns the magic number.
     * 
     * @return the magic number
     */
    public MagicNumber getMagicNumber() {
        return magicNumber;
    }

    /**
     * Returns the magic number description.
     * 
     * @param magicNumber
     * @return the magic number description
     */
    public static String getMagicNumberString(MagicNumber magicNumber) {
        switch (magicNumber) {
        case PE32:
            return "PE32, normal executable file";
        case PE32_PLUS:
            return "PE32+ executable";
        case ROM:
            return "ROM image";
        default:
            throw new IllegalArgumentException(
                    "unable to recognize magic number");
        }
    }

    /**
     * Returns the description string of the image base.
     * 
     * @param value
     * @return description string of the image base value
     */
    public static String getImageBaseDescription(long value) {
        if (value == 0x10000000)
            return "DLL default";
        if (value == 0x00010000)
            return "default for Windows CE EXEs";
        if (value == 0x00400000)
            return "default for Windows NT, 2000, XP, 95, 98 and Me";
        return "no default value";
    }

    /**
     * Checks if image base is too large or zero and relocates it accordingly.
     * Otherwise the usual image base is returned.
     * 
     * see: @see <a
     * href="https://code.google.com/p/corkami/wiki/PE#ImageBase">corkami</a>
     * 
     * @return relocated image base
     */
    public long getRelocatedImageBase() {
        long imageBase = get(WindowsEntryKey.IMAGE_BASE);
        long sizeOfImage = get(WindowsEntryKey.SIZE_OF_IMAGE);
        if (imageBase + sizeOfImage >= 0x80000000L || imageBase == 0L) {
            return 0x10000L;
        }
        return imageBase;
    }

    /**
     * Returns a list of the DllCharacteristics that are set in the file.
     * 
     * @return list of DllCharacteristics
     */
    public List<DllCharacteristic> getDllCharacteristics() {
        List<DllCharacteristic> dllChs = new ArrayList<>();
        long characteristics = get(DLL_CHARACTERISTICS);
        List<String> keys = IOUtil.getCharacteristicKeys(characteristics,
                DLL_CHARACTERISTICS_SPEC);
        for (String key : keys) {
            dllChs.add(DllCharacteristic.valueOf(key));
        }
        return dllChs;
    }

    /**
     * Returns a list of all DllCharacteristic descriptions.
     * 
     * @return list of DllCharacteristic descriptions
     */
    public List<String> getDllCharacteristicsDescriptions() {
        return IOUtil.getCharacteristicsDescriptions(get(DLL_CHARACTERISTICS),
                DLL_CHARACTERISTICS_SPEC);
    }

    /**
     * Returns the description string of the subsystem value.
     * 
     * @param value
     * @return subsystem description string
     */
    public static String getSubsystemDescription(int value) {
        try {
            Map<String, String[]> map = IOUtil.readMap(SUBSYSTEM_SPEC);
            return map.get(String.valueOf(value))[1];
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException(
                "unable to find subsystem description");
    }

    /**
     * Returns the subsystem key string for the given value.
     * 
     * @param value
     * @return key string
     */
    public static String getSubsystemKey(int value) {
        try {
            Map<String, String[]> map = IOUtil.readMap(SUBSYSTEM_SPEC);
            return map.get(String.valueOf(value))[0];
        } catch (IOException e) {
            logger.error(e);
        }
        throw new IllegalArgumentException("unable to find subsystem key");
    }

    /**
     * Returns the subsystem instance of the file.
     * 
     * @return subsystem instance
     */
    public Subsystem getSubsystem() {
        long subsystem = get(SUBSYSTEM);
        String key = getSubsystemKey((int) subsystem);
        return Subsystem.valueOf(key);
    }

    @Override
    public long getOffset() {
        return offset;
    }

    public int getMinSize() {
        return minSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public long getSize() {
        return headerbytes.length;
    }

    /**
     * Adjusts the file alignment to low alignment mode if necessary.
     * 
     * @return 1 if low alignment mode, file alignment value otherwise
     */
    public long getAdjustedFileAlignment() {
        long fileAlign = get(FILE_ALIGNMENT);
        if (isLowAlignmentMode()) {
            return 1;
        }
        return fileAlign;
    }

    /**
     * Determines if the file is in low alignment mode.
     * 
     * @see <a href="https://code.google.com/p/corkami/wiki/PE#SectionAlignment_/_FileAlignment">corkami Wiki PE</a>
     * @return true iff file is in low alignment mode
     */
    public boolean isLowAlignmentMode() {
        long fileAlign = get(FILE_ALIGNMENT);
        long sectionAlign = get(SECTION_ALIGNMENT);
        return 1 <= fileAlign && fileAlign == sectionAlign && fileAlign <= 0x800;
    }
    
    /**
     * Determines if the file is in standard alignment mode.
     * 
     * @see <a href="https://code.google.com/p/corkami/wiki/PE#SectionAlignment_/_FileAlignment">corkami Wiki PE</a>
     * @return true iff file is in standard alignment mode
     */
    public boolean isStandardAlignmentMode() {
        long fileAlign = get(FILE_ALIGNMENT);
        long sectionAlign = get(SECTION_ALIGNMENT);
        return 0x200 <= fileAlign && fileAlign <= sectionAlign && 0x1000 <= sectionAlign;
    }
}
