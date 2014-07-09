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
package com.github.katjahahn.parser.sections.idata;

/**
 * Represents an import by ordinal.
 * 
 * @author Katja Hahn
 *
 */
public class OrdinalImport implements Import {

    /**
     * The ordinal number of the import
     */
	public int ordinal;
	
	/**
	 * The relative virtual address of the symbol
	 */
	public long rva;
	
	/**
	 * The directory entry this import belongs to
	 */
	private final DirectoryEntry parent;
	
	/**
	 * 
	 * @param ordinal the ordinal number of the import
	 * @param rva the rva of the symbol
	 * @param parent the directory entry this import belongs to
	 */
	public OrdinalImport(int ordinal, long rva, DirectoryEntry parent) {
		this.ordinal = ordinal;
		this.rva = rva;
		this.parent = parent;
	}
	
	/**
	 * Returns the value of the directory entry field
	 */
	public Long getDirEntryValue(DirectoryEntryKey key) {
		return parent.get(key);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "ordinal: " + ordinal + ", rva: " + rva + " (0x" + Long.toHexString(rva) + ")";
	}
}
