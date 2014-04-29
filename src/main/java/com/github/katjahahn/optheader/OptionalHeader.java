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

import com.github.katjahahn.HeaderKey;
import com.github.katjahahn.IOUtil;
import com.github.katjahahn.PEModule;
import com.github.katjahahn.StandardEntry;

public class OptionalHeader extends PEModule {

	/* spec locations */
	private static final String SUBSYSTEM_SPEC = "subsystem";
	private static final String DLL_CHARACTERISTICS_SPEC = "dllcharacteristics";
	private static final String STANDARD_SPEC = "optionalheaderstandardspec";
	private static final String WINDOWS_SPEC = "optionalheaderwinspec";
	private static final String DATA_DIR_SPEC = "datadirectoriesspec";

	/* extracted file data */
	private Map<DataDirectoryKey, DataDirEntry> dataDirEntries;
	private Map<StandardFieldEntryKey, StandardEntry> standardFields;
	private Map<WindowsEntryKey, StandardEntry> windowsFields;

	private final byte[] headerbytes;
	private MagicNumber magicNumber;
	private int rvaNumber;

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

	public OptionalHeader(byte[] headerbytes) {
		this.headerbytes = headerbytes.clone();
	}

	@Override
	public void read() throws IOException {
		Map<String, String[]> standardSpec = IOUtil.readMap(STANDARD_SPEC);
		Map<String, String[]> windowsSpec = IOUtil.readMap(WINDOWS_SPEC);
		List<String[]> datadirSpec = IOUtil.readArray(DATA_DIR_SPEC);

		this.magicNumber = readMagicNumber(standardSpec);

		if (!magicNumber.equals(MagicNumber.PE32)) {
			standardSpec.remove("BASE_OF_DATA");
		}

		loadStandardFields(standardSpec);
		loadWindowsSpecificFields(windowsSpec);
		loadDataDirectories(datadirSpec);
	}

	/**
	 * 
	 * @return the data directory entries
	 */
	public Map<DataDirectoryKey, DataDirEntry> getDataDirEntries() {
		return new HashMap<>(dataDirEntries);
	}

	/**
	 * 
	 * @return the windows specific fields
	 */
	public Map<WindowsEntryKey, StandardEntry> getWindowsSpecificFields() {
		return new HashMap<>(windowsFields);
	}

	/**
	 * 
	 * @return the standard fields
	 */
	public Map<StandardFieldEntryKey, StandardEntry> getStandardFields() {
		return new HashMap<>(standardFields);
	}

	/**
	 * Returns the data directory entry for the given key. //TODO use map
	 * 
	 * @param key
	 * @return the data directory entry for the given key. null if key doesn't
	 *         exist.
	 */
	public DataDirEntry getDataDirEntry(DataDirectoryKey key) {
		return dataDirEntries.get(key);
	}

	/**
	 * Returns either a windows field entry value or a standard field entry
	 * value, depending on the given key.
	 * 
	 * @param key
	 * @return the windows field entry value or the standard field entry value
	 *         that belongs to the given key, null if there is no
	 *         {@link StandardEntry} for this key available
	 */
	@Override
	public Long get(HeaderKey key) {
		StandardEntry standardEntry = null;
		if (key instanceof StandardFieldEntryKey) {
			standardEntry = standardFields.get(key);

		} else {
			standardEntry = windowsFields.get(key);
		}
		if (standardEntry != null) {
			return standardEntry.value;
		}
		return null;
	}

	/**
	 * Returns the standard field entry for the given key. //TODO use map
	 * 
	 * @param key
	 * @return the standard field entry for the given key, null if key doesn't
	 *         exist.
	 */
	public StandardEntry getStandardFieldEntry(StandardFieldEntryKey key) {
		return standardFields.get(key);
	}

	/**
	 * Returns the windows field entry for the given key.
	 * 
	 * @param key
	 * @return the windows field entry for the given key, null if key doesn't
	 *         exist
	 */
	public StandardEntry getWindowsFieldEntry(WindowsEntryKey key) {
		return windowsFields.get(key);
	}

	private void loadStandardFields(Map<String, String[]> standardSpec) {
		standardFields = new HashMap<>();
		int description = 0;
		int offsetLoc = 1;
		int lengthLoc = 2;

		for (Entry<String, String[]> entry : standardSpec.entrySet()) {
			String[] specs = entry.getValue();
			long value = getBytesLongValue(headerbytes,
					Integer.parseInt(specs[offsetLoc]),
					Integer.parseInt(specs[lengthLoc]));
			StandardFieldEntryKey key = StandardFieldEntryKey.valueOf(entry
					.getKey());
			standardFields.put(key, new StandardEntry(key, specs[description],
					value));
		}

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
			long address = getBytesLongValue(headerbytes,
					Integer.parseInt(specs[offsetLoc]), length);
			long size = getBytesLongValue(headerbytes,
					Integer.parseInt(specs[offsetLoc]) + length, length);
			if (address != 0) {
				DataDirEntry entry = new DataDirEntry(specs[description],
						address, size);
				dataDirEntries.put(entry.key, entry);
			}
			counter++;
		}
	}

	private void loadWindowsSpecificFields(Map<String, String[]> windowsSpec) {
		windowsFields = new HashMap<>();
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
			long value = getBytesLongValue(headerbytes,
					Integer.parseInt(specs[offsetLoc]),
					Integer.parseInt(specs[lengthLoc]));
			WindowsEntryKey key = WindowsEntryKey.valueOf(entry.getKey());
			windowsFields.put(key, new StandardEntry(key, specs[description],
					value));
			if (key.equals(NUMBER_OF_RVA_AND_SIZES)) {
				this.rvaNumber = (int) value; // always 4 Bytes
			}
		}
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
	 * A description of all data directories.
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
		for (StandardEntry entry : windowsFields.values()) {
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
				b.append(IOUtil.getCharacteristics(value, DLL_CHARACTERISTICS_SPEC)
						+ NL);
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
		for (StandardEntry entry : standardFields.values()) {
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
		return null;
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

	public List<DllCharacteristic> getDllCharacteristics() {
		List<String> keys = IOUtil.getCharacteristicKeys(
				get(DLL_CHARACTERISTICS), DLL_CHARACTERISTICS_SPEC);
		List<DllCharacteristic> dllChs = new ArrayList<>();
		for (String key : keys) {
			dllChs.add(DllCharacteristic.valueOf(key));
		}
		return dllChs;
	}

	public List<String> getDllCharacteristicsDescriptions() {
		return IOUtil.getCharacteristicsDescriptions(
				get(DLL_CHARACTERISTICS), DLL_CHARACTERISTICS_SPEC);
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
		return null;
	}

	public static String getSubsystemKey(int value) {
		try {
			Map<String, String[]> map = IOUtil.readMap(SUBSYSTEM_SPEC);
			return map.get(String.valueOf(value))[0];
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Subsystem getSubsystem() {
		return Subsystem.valueOf(getSubsystemKey(get(SUBSYSTEM).intValue()));
	}
}
