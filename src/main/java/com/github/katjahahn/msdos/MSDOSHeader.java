package com.github.katjahahn.msdos;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.katjahahn.FileIO;
import com.github.katjahahn.PEModule;
import com.github.katjahahn.StandardEntry;

public class MSDOSHeader extends PEModule {

	// Note: This is only the formatted header by now. The actual header may be
	// larger, containing optional values.
	public static final int FORMATTED_HEADER_SIZE = 28;
	
	private static final int PARAGRAPH_SIZE = 16;

	private static final byte[] MZ_SIGNATURE = "MZ".getBytes();
	private static final String specification = "msdosheaderspec";
	private static Map<String, StandardEntry> headerData;

	private final byte[] headerbytes;

	public MSDOSHeader(byte[] headerbytes) {
		this.headerbytes = headerbytes;
	}
	
	@Override
	public void read() throws IOException {
		if(!hasSignature(headerbytes)) {
			throw new IOException("No PE Signature found");
		}
		headerData = new HashMap<>();
		int offsetLoc = 0;
		int sizeLoc = 1;
		int descriptionLoc = 2;
		try {
			Map<String, String[]> map = FileIO.readMap(specification);
			for (Entry<String, String[]> entry : map.entrySet()) {
				String key = entry.getKey();
				String[] spec = entry.getValue();
				int value = getBytesIntValue(headerbytes,
						Integer.parseInt(spec[offsetLoc]),
						Integer.parseInt(spec[sizeLoc]));
				headerData.put(key, new StandardEntry(key,
						spec[descriptionLoc], value));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}

	//TODO verify
	public int getHeaderSize() {
		return get("HEADER_PARAGRAPHS").value * PARAGRAPH_SIZE;
	}

	private boolean hasSignature(byte[] headerbytes) {
		if (headerbytes.length < 28) {
			throw new IllegalArgumentException(
					"not enough headerbytes for MS DOS Header");
		} else {
			for (int i = 0; i < MZ_SIGNATURE.length; i++) {
				if (MZ_SIGNATURE[i] != headerbytes[i]) {
					return false;
				}
			}
			return true;
		}
	}

	public List<StandardEntry> getHeaderEntries() {
		return new LinkedList<>(headerData.values());
	}

	public StandardEntry get(String keyString) {
		return headerData.get(keyString);
	}

	@Override
	public String getInfo() {
		if (headerData == null) {
			return "No MS DOS Header found!" + NL;
		} else {
			StringBuilder b = new StringBuilder("-------------" + NL
					+ "MS DOS Header" + NL + "-------------" + NL);
			for (StandardEntry entry : headerData.values()) {
				b.append(entry.description + ": " + entry.value + " (0x"
						+ Integer.toHexString(entry.value) + ")" + NL);
			}
			return b.toString();
		}
	}

}
