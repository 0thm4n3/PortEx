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
package com.github.katjahahn.msdos;

import static com.github.katjahahn.ByteArrayUtil.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.katjahahn.IOUtil;
import com.github.katjahahn.PEModule;
import com.github.katjahahn.StandardEntry;

/**
 * Fetches values from the MSDOS header of the PE.
 * 
 * @author Katja Hahn
 *
 */
public class MSDOSHeader extends PEModule {

	// Note: This is only the formatted header by now. The actual header may be
	// larger, containing optional values.
	public static final int FORMATTED_HEADER_SIZE = 28;
	private static final int PARAGRAPH_SIZE = 16; //in Byte

	private static final byte[] MZ_SIGNATURE = "MZ".getBytes();
	private static final String specification = "msdosheaderspec";
	private Map<MSDOSHeaderKey, StandardEntry> headerData;

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
			Map<String, String[]> map = IOUtil.readMap(specification);
			for (Entry<String, String[]> entry : map.entrySet()) {
				MSDOSHeaderKey key = MSDOSHeaderKey.valueOf(entry.getKey());
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

	public int getHeaderSize() {
		return get(MSDOSHeaderKey.HEADER_PARAGRAPHS) * PARAGRAPH_SIZE;
	}

	private boolean hasSignature(byte[] headerbytes) {
		if (headerbytes == null || headerbytes.length < 28) {
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

	public int get(MSDOSHeaderKey key) {
		return (int) headerData.get(key).value; //no 8 Byte values in MSDOS header
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
						+ Long.toHexString(entry.value) + ")" + NL);
			}
			return b.toString();
		}
	}

}
