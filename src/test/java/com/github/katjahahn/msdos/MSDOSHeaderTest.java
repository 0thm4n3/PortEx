package com.github.katjahahn.msdos;

import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.github.katjahahn.IOUtil;
import com.github.katjahahn.IOUtil.TestData;
import com.github.katjahahn.PEData;
import com.github.katjahahn.PELoader;
import com.github.katjahahn.StandardEntry;

public class MSDOSHeaderTest {

	private List<TestData> testdata;
	private final Map<String, PEData> pedata = new HashMap<>();

	@BeforeClass
	public void prepare() throws IOException {
		File[] testfiles = IOUtil.getTestiles();
		for (File file : testfiles) {
			pedata.put(file.getName(), PELoader.loadPE(file));
		}
		testdata = IOUtil.readTestDataList();
	}

	@Test
	public void get() {
		for (TestData testdatum : testdata) {
			PEData pedatum = pedata.get(testdatum.filename.replace(".txt", ""));
			for (Entry<MSDOSHeaderKey, String> entry : testdatum.dos.entrySet()) {
				MSDOSHeaderKey key = entry.getKey();
				MSDOSHeader dos = pedatum.getMSDOSHeader();
				int actual = dos.get(key).value;
				String value = entry.getValue().trim();
				int expected = convertToInt(value);
				assertEquals(expected, actual);
			}
		}
	}

	@Test(expectedExceptions = IOException.class)
	public void noPEFile() throws IOException {
		PELoader.loadPE(new File("build.sbt"));
	}

	@Test(expectedExceptions = IllegalArgumentException.class, 
			expectedExceptionsMessageRegExp = "not enough headerbytes for MS DOS Header")
	public void headerBytesTooShort() throws IOException {
		byte[] headerbytes = { 1, 2, 3 };
		new MSDOSHeader(headerbytes).read();
	}

	@Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = "No PE Signature found")
	public void invalidHeaderBytes() throws IOException {
		byte[] headerbytes = new byte[28];
		for (int i = 0; i < headerbytes.length; i++) {
			headerbytes[i] = (byte) i;
		}
		new MSDOSHeader(headerbytes).read();
	}

	private int convertToInt(String value) {
		if (value.startsWith("0x")) {
			value = value.replace("0x", "");
			return Integer.parseInt(value, 16);
		} else {
			return Integer.parseInt(value);
		}
	}

	@Test
	public void getInfo() {
		String info = pedata.get("strings.exe").getMSDOSHeader().getInfo();
		assertNotNull(info);
		assertTrue(info.length() > 0);
		String noHeaderBytes = new MSDOSHeader(null).getInfo();
		System.out.println(noHeaderBytes);
		assertTrue(noHeaderBytes.equals("No MS DOS Header found!" + IOUtil.NL));
	}
	
	@Test
	public void getHeaderEntries() {
		List<StandardEntry> list = pedata.get("strings.exe").getMSDOSHeader()
				.getHeaderEntries();
		assertNotNull(list);
		assertEquals(list.size(), MSDOSHeaderKey.values().length);
	}
}
