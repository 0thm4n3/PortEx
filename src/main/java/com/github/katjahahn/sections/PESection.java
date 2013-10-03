package com.github.katjahahn.sections;

import com.github.katjahahn.PEModule;

public class PESection extends PEModule {

	private byte[] sectionbytes;
	
	protected PESection() {}

	public PESection(byte[] sectionbytes) {
		this.sectionbytes = sectionbytes;
	}
	
	public byte[] getDump() {
		return sectionbytes;
	}

	@Override
	public String getInfo() {
		return null;
	}

}
