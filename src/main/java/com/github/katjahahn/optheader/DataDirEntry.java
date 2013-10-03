package com.github.katjahahn.optheader;

import static com.github.katjahahn.PEModule.*;
import static com.github.katjahahn.sections.SectionTableEntryKey.*;

import java.util.List;

import com.github.katjahahn.sections.SectionTable;
import com.github.katjahahn.sections.SectionTableEntry;

public class DataDirEntry {

	public DataDirectoryKey fieldName;
	public int virtualAddress; // RVA actually, but called like this in spec
	public int size;

	public DataDirEntry(String fieldName, int virtualAddress, int size) {
		this(DataDirectoryKey.valueOf(fieldName), virtualAddress, size);
	}
	
	public DataDirEntry(DataDirectoryKey fieldName, int virtualAddress, int size) {
		this.fieldName = fieldName;
		this.virtualAddress = virtualAddress;
		this.size = size;
	}

	public int getFileOffset(SectionTable table) {
		SectionTableEntry section = getSectionTableEntry(table);
		int sectionRVA = section.get(VIRTUAL_ADDRESS);
		int sectionOffset = section.get(POINTER_TO_RAW_DATA);
		return (virtualAddress - sectionRVA) + sectionOffset;
	}

	public SectionTableEntry getSectionTableEntry(SectionTable table) {
		List<SectionTableEntry> sections = table.getSectionEntries();
		for (SectionTableEntry section : sections) {
			int vSize = section.get(VIRTUAL_SIZE);
			int vAddress = section.get(VIRTUAL_ADDRESS);
			if (rvaIsWithin(vAddress, vSize)) {
				return section;
			}
		}
		return null;
	}

	private boolean rvaIsWithin(int address, int size) {
		int endpoint = address + size;
		return virtualAddress >= address && virtualAddress < endpoint;
	}

	@Override
	public String toString() {
		return "field name: " + fieldName + NL + "virtual address: "
				+ virtualAddress + NL + "size: " + size;
	}
}
