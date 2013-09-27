package com.github.katjahahn.pemodules;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import com.github.katjahahn.FileIO;

public class RSRCSection extends PEModule {

	private final static String RSRC_DIR_SPEC = "rsrcdirspec";
	private final static String RSRC_DIR_ENTRY_SPEC = "resourcedirentryspec";
	private final static String RSRC_DATA_ENTRY_SPEC = "resourcedataentryspec";
	private final static int ENTRY_SIZE = 8;
	private final static int RESOURCE_DIR_OFFSET = 16;
	private Map<String, String[]> rsrcDirSpec;
	private Map<String, String[]> resourceDirEntrySpec;
	private Map<String, String[]> resourceDataEntrySpec;
	private final byte[] rsrcbytes;
	private final byte[] filebytes;
	private final int virtualAddress;
	private final boolean lastEntryWasDataEntryRVA = false;

	public RSRCSection(byte[] rsrcbytes, byte[] filebytes, int virtualAddress) {
		this.rsrcbytes = rsrcbytes;
		this.filebytes = filebytes;
		this.virtualAddress = virtualAddress;
		try {
			rsrcDirSpec = FileIO.readMap(RSRC_DIR_SPEC);
			resourceDirEntrySpec = FileIO.readMap(RSRC_DIR_ENTRY_SPEC);
			resourceDataEntrySpec = FileIO.readMap(RSRC_DATA_ENTRY_SPEC);
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getInfo() {
		return getResourceDirTableInfo(rsrcbytes, "root");
	}

	private String getResourceDirEntriesInfo(byte[] entryBytes,
			int nameEntries, int idEntries) {
		StringBuilder b = new StringBuilder();
		b.append(NEWLINE + "** table entries **" + NEWLINE + NEWLINE);
		entryBytes = Arrays.copyOfRange(entryBytes, RESOURCE_DIR_OFFSET,
				entryBytes.length);
		for (int i = 0; i < nameEntries + idEntries; i++) {
			b.append("entry number " + (i + 1) + NEWLINE + NEWLINE);
			if (i < nameEntries) {
				b.append(getResourceDirEntryInfo(entryBytes, true) + NEWLINE);
			} else {
				b.append(getResourceDirEntryInfo(entryBytes, false) + NEWLINE);
			}
			entryBytes = Arrays.copyOfRange(entryBytes, ENTRY_SIZE,
					entryBytes.length);
		}
		return b.toString();
	}

	private String getResourceDirEntryInfo(byte[] entryBytes,
			boolean isNameEntry) {
		StringBuilder b = new StringBuilder();
		final int description = isNameEntry ? 0 : 1;
		int dataEntryRvaDescription = 0;
		int idEntryDescription = 1;
		int valueOffset = 2;
		int valueSize = 3;
		String showAfterwards = "";
		String tableId = "unknown subtable";

		for (Entry<String, String[]> entry : resourceDirEntrySpec.entrySet()) {
			String[] specs = entry.getValue();
			long value = getBytesLongValue(entryBytes,
					Integer.parseInt(specs[valueOffset]),
					Integer.parseInt(specs[valueSize]));
			String key = entry.getKey();
			if (key.equals("DATA_ENTRY_RVA_OR_SUBDIR_RVA")) {
				showAfterwards = appendSubDirOrDataEntryRvaInfo(b,
						dataEntryRvaDescription, idEntryDescription, specs,
						value, tableId);
			} else {
				b.append(specs[description] + ": " + value + NEWLINE);
				tableId = Long.toString(value);
			}
		}

		b.append(showAfterwards);
		return b.toString();
	}

	private String appendSubDirOrDataEntryRvaInfo(StringBuilder b,
			int dataEntryRvaDescription, int idEntryDescription,
			String[] specs, long value, String tableId) {

		if (isDataEntryRVA(value)) {
			b.append(specs[dataEntryRvaDescription] + ": " + value + " (0x"
					+ Long.toHexString(value) + ")" + NEWLINE);
			byte[] resourceBytes = Arrays.copyOfRange(rsrcbytes, (int) value,
					rsrcbytes.length);
			return getResourceDataEntry(resourceBytes);
		} else {
			value = removeHighestBit(value);
			b.append(specs[idEntryDescription] + ": " + value + " (0x"
					+ Long.toHexString(value) + ")" + NEWLINE);
			byte[] resourceBytes = Arrays.copyOfRange(rsrcbytes,
					(int) value, rsrcbytes.length);
			return NEWLINE + getResourceDirTableInfo(resourceBytes, tableId);
		}
	}

	//TODO resource dir strings
	
	private String getResourceDataEntry(byte[] resourceBytes) {
		StringBuilder b = new StringBuilder();
		int pointer = 0;
		int size = 0;
		b.append(NEWLINE + "** resource data **" + NEWLINE + NEWLINE);
		for (Entry<String, String[]> entry : resourceDataEntrySpec.entrySet()) {
			String[] specs = entry.getValue();
			String key = entry.getKey();
			int value = getBytesIntValue(resourceBytes,
					Integer.parseInt(specs[1]), Integer.parseInt(specs[2]));
			if (key.equals("DATA_RVA")) {
				pointer = value - virtualAddress;
			}
			if (key.equals("SIZE")) {
				size = value;
			}
			b.append(specs[0] + ": " + value + " (0x"
					+ Integer.toHexString(value) + ")" + NEWLINE);
		}
		if (pointer != 0 && size != 0) {
			showResource(b, pointer, size);
		}
		return b.toString();
	}

	private void showResource(StringBuilder b, int pointer, int size) {
		byte[] resource = Arrays.copyOfRange(rsrcbytes, pointer, pointer + size);
		try {
			b.append(NEWLINE + NEWLINE + new String(resource, "UTF8").trim() + NEWLINE + NEWLINE);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	// TODO this only works for 4 byte numbers, please test this
	private long removeHighestBit(long value) {
		long mask = 0x7FFFFFFF;
		return (value & mask);
	}

	private boolean isDataEntryRVA(long value) {
		int mask = 1 << 31;
		return (value & mask) == 0;
	}

	private String getResourceDirTableInfo(byte[] tableBytes, String id) {
		StringBuilder b = new StringBuilder();
		int nameEntries = 0;
		int idEntries = 0;

		b.append("** table header " + id + " **" + NEWLINE + NEWLINE);
		for (Entry<String, String[]> entry : rsrcDirSpec.entrySet()) {

			String[] specs = entry.getValue();
			int value = getBytesIntValue(tableBytes,
					Integer.parseInt(specs[1]), Integer.parseInt(specs[2]));
			String key = entry.getKey();

			if (key.equals("TIME_DATE_STAMP")) {
				b.append(specs[0] + ": ");
				b.append(getTimeDate(value) + NEWLINE);
			} else {
				b.append(specs[0] + ": " + value + NEWLINE);
			}

			if (key.equals("NR_OF_NAME_ENTRIES")) {
				nameEntries = value;
			} else if (key.equals("NR_OF_ID_ENTRIES")) {
				idEntries = value;
			}
		}
		if (nameEntries != 0 || idEntries != 0) {
			b.append(getResourceDirEntriesInfo(tableBytes, nameEntries,
					idEntries));
		}
		return b.toString();
	}

	private Date getTimeDate(int seconds) {
		long millis = (long) seconds * 1000;
		return new Date(millis);
	}

}
