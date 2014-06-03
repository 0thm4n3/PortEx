package com.github.katjahahn.sections.edata;

import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.github.katjahahn.PEData;
import com.github.katjahahn.PELoader;
import com.github.katjahahn.PELoaderTest;
import com.github.katjahahn.TestreportsReader;
import com.github.katjahahn.optheader.WindowsEntryKey;
import com.github.katjahahn.sections.SectionLoader;

public class ExportSectionTest {

	private static final Logger logger = LogManager
			.getLogger(ExportSectionTest.class.getName());
	private Map<File, List<ExportEntry>> exportEntries;
	private Map<String, PEData> pedata = new HashMap<>();

	@BeforeClass
	public void prepare() throws IOException {
		exportEntries = TestreportsReader.readExportEntries();
		pedata = PELoaderTest.getPEData();
	}
	
	@Test
	public void forwarderTest() throws IOException {
		File forwarder = new File("src/main/resources/unusualfiles/corkami/forwarder.dll");
		PEData data = PELoader.loadPE(forwarder);
		ExportSection edata = ExportSection.load(data);
		List<ExportEntry> exportEntries = edata.getExportEntries();
		for(ExportEntry export : exportEntries) {
			assertTrue(export.forwarded());
		}
		
		File nonforwarder = new File("src/main/resources/testfiles/DLL2.dll");
		data = PELoader.loadPE(nonforwarder);
		edata = ExportSection.load(data);
		exportEntries = edata.getExportEntries();
		for(ExportEntry export : exportEntries) {
			assertFalse(export.forwarded());
		}
		
	}

	@Test
	public void getExportEntries() throws IOException {
		// assertEquals(pedata.size(), exportEntries.size());
		for (Entry<File, List<ExportEntry>> set : exportEntries.entrySet()) {
			File file = set.getKey();
			List<ExportEntry> expected = set.getValue();
			String filename = file.getName().replace(".txt", "");
			PEData datum = pedata.get(filename);
			SectionLoader loader = new SectionLoader(datum);
			ExportSection edata = loader.loadExportSection();
			if (edata == null) {
				assertTrue(expected.size() == 0);
			} else {
				logger.info("testing entries for " + filename);
				expected = substractImageBase(expected, datum);
				List<ExportEntry> actual = edata.getExportEntries();
				assertEquals(actual, expected);
			}

		}
	}

	//Patches the expected list to match our RVA that has not the image base added
	private List<ExportEntry> substractImageBase(List<ExportEntry> expected, PEData datum) {
		List<ExportEntry> list = new ArrayList<ExportEntry>();
		long imageBase = datum.getOptionalHeader().getWindowsFieldEntry(WindowsEntryKey.IMAGE_BASE).get().value;
		for(ExportEntry entry : expected) {
			list.add(new ExportEntry(entry.symbolRVA() - imageBase, entry.name(), entry.ordinal(), false));
		}
		return list;
	}
}
