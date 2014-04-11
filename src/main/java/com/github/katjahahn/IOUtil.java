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
package com.github.katjahahn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for file IO needed to read maps and arrays from the text files in
 * the data subdirectory of PortEx.
 * 
 * The specification text files are CSV, where the values are separated by
 * semicolon and a new entry begins on a new line.
 * 
 * The report files for testing are done with the tool pev.
 * 
 * @author Katja Hahn
 * 
 */
public class IOUtil {

	private static final Logger logger = LogManager.getLogger(IOUtil.class.getName());
	public static final String NL = System.getProperty("line.separator");
	// TODO system independend path separators
	private static final String DELIMITER = ";";
	private static final String SPEC_DIR = "/data/";
	

	/**
	 * Reads the specified file into a map. The first value is used as key. The
	 * rest is put into a list and used as map value. Each entry is one line of
	 * the file.
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static Map<String, String[]> readMap(String filename)
			throws IOException {
		Map<String, String[]> map = new TreeMap<>();
		
		try (InputStreamReader isr = new InputStreamReader(
				IOUtil.class.getResourceAsStream(SPEC_DIR + filename));
				BufferedReader reader = new BufferedReader(isr)) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(DELIMITER);
				map.put(values[0], Arrays.copyOfRange(values, 1, values.length));
			}
			return map;
		}
	}

	/**
	 * Reads the specified file into a list of arrays. Each array is the entry
	 * of one line in the file.
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static List<String[]> readArray(String filename) throws IOException {
		List<String[]> list = new LinkedList<>();
		try (InputStreamReader isr = new InputStreamReader(
				IOUtil.class.getResourceAsStream(SPEC_DIR + filename));
				BufferedReader reader = new BufferedReader(isr)) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(DELIMITER);
				list.add(values);
			}
			return list;
		}
	}

	public static List<String> getCharacteristicsDescriptions(long value,
			String filename) {
		List<String> characteristics = new LinkedList<>();
		try {
			Map<String, String[]> map = readMap(filename);
			for (Entry<String, String[]> entry : map.entrySet()) {
				try {
					long mask = Long.parseLong(entry.getKey(), 16);
					if ((value & mask) != 0) {
						characteristics.add(entry.getValue()[1]);
					}
				} catch (NumberFormatException e) {
					System.err.println("ERROR. number format mismatch in file "
							+ filename + NL);
					System.err.println("value: " + entry.getKey() + NL);
				}
			}
		} catch (IOException e) {
			logger.error(e);
		}
		return characteristics;
	}

	public static String getCharacteristics(long value, String filename) {
		StringBuilder b = new StringBuilder();
		try {
			Map<String, String[]> map = readMap(filename);
			for (Entry<String, String[]> entry : map.entrySet()) {
				try {
					long mask = Long.parseLong(entry.getKey(), 16);
					if ((value & mask) != 0) {
						b.append("\t* " + entry.getValue()[1] + NL);
					}
				} catch (NumberFormatException e) {
					b.append("ERROR. number format mismatch in file "
							+ filename + NL);
					b.append("value: " + entry.getKey() + NL);
				}
			}
		} catch (IOException e) {
			logger.error(e);
		}
		if (b.length() == 0) {
			b.append("\t**no characteristics**" + NL);
		}
		return b.toString();
	}

}
