package com.github.katjahahn.sections.rsrc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.github.katjahahn.FileIO;
import com.github.katjahahn.PEModule;
import com.github.katjahahn.StandardEntry;

public class ResourceDataEntry extends PEModule {

	public static final int SIZE = 16;
	private final static String RSRC_DATA_ENTRY_SPEC = "resourcedataentryspec";
	private Map<String, String[]> resourceDataEntrySpec;
	private Map<ResourceDataEntryKey, StandardEntry> data;

	public ResourceDataEntry(byte[] entryBytes) {
		try {
			resourceDataEntrySpec = FileIO.readMap(RSRC_DATA_ENTRY_SPEC);
			load(entryBytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void load(byte[] entryBytes) {
		data = new HashMap<>();
		for (Entry<String, String[]> entry : resourceDataEntrySpec.entrySet()) {
			String[] specs = entry.getValue();
			String key = entry.getKey();
			int value = getBytesIntValue(entryBytes,
					Integer.parseInt(specs[1]), Integer.parseInt(specs[2]));
			String description = specs[0];
			data.put(ResourceDataEntryKey.valueOf(key), new StandardEntry(key, description, value));
		}
	}
	
	public StandardEntry get(ResourceDataEntryKey key) {
		return data.get(key);
	}
	
	@Override
	public String getInfo() {
		StringBuilder b = new StringBuilder();
		b.append(NL + "** resource data **" + NL + NL);
		for (StandardEntry entry : data.values()) {
			int value = entry.value;

			b.append(entry.description + ": " + value + " (0x"
					+ Integer.toHexString(value) + ")" + NL);
		}
		return b.toString();
	}

}
