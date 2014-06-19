package com.github.katjahahn;

import static com.github.katjahahn.tools.anomalies.AnomalyType.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.katjahahn.parser.FileFormatException;
import com.github.katjahahn.parser.PEData;
import com.github.katjahahn.parser.PELoader;
import com.github.katjahahn.parser.PESignature;
import com.github.katjahahn.parser.coffheader.COFFFileHeader;
import com.github.katjahahn.parser.coffheader.FileCharacteristic;
import com.github.katjahahn.parser.optheader.DataDirEntry;
import com.github.katjahahn.parser.optheader.DataDirectoryKey;
import com.github.katjahahn.parser.optheader.OptionalHeader.MagicNumber;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.tools.Overlay;
import com.github.katjahahn.tools.anomalies.Anomaly;
import com.github.katjahahn.tools.anomalies.AnomalyType;
import com.github.katjahahn.tools.anomalies.PEAnomalyScanner;

public class PortexStats {

	private static final Logger logger = LogManager.getLogger(PortexStats.class
			.getName());

	private static final String BASE_MALW_FOLDER = "/home/deque/virusshare128";
	@SuppressWarnings("unused")
	private static final String ANOMALY_FOLDER = "/home/deque/portextestfiles/unusualfiles/corkami";
	private static final String PE_FOLDER = BASE_MALW_FOLDER + "/pe/";
	private static final String NO_PE_FOLDER = BASE_MALW_FOLDER + "/nope/";
	private static final String STATS_FOLDER = "portexstats/";
	private static int noPE = 0;
	private static int notLoaded = 0;
	private static int dirsRead = 0;
	private static int total = 0;
	private static int prevTotal = 0;
	private static int written = 0;

	public static void main(String[] args) throws IOException {
		anomalyCount(new File(PE_FOLDER).listFiles());
	}

	public static void fileTypeCountForFileList() throws IOException {
		List<File> files = readFileList();
		fileTypeCount(files.toArray((new File[files.size()])));
	}

	public static List<File> readFileList() throws IOException {
		System.out.println("reading file list");
		Path filelist = Paths.get(STATS_FOLDER, "pefilelist");
		List<File> files = new ArrayList<File>();
		Charset charset = Charset.forName("US-ASCII");
		try (BufferedReader reader = Files.newBufferedReader(filelist, charset)) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				files.add(new File(line));
			}
		}
		System.out.println("Done reading");
		return files;
	}

	public static void overlayPrevalenceForFileList() throws IOException {
		List<File> files = readFileList();
		overlayPrevalence(files.toArray((new File[files.size()])));
	}

	public static void anomalyStatsForFileList() throws IOException {
		List<File> files = readFileList();
		anomalyStats(files.toArray((new File[files.size()])));
	}

	public static void createPEFileList(File startFolder) {
		Charset charset = Charset.forName("UTF-8");
		Path out = Paths.get(STATS_FOLDER, "pefilelist");
		try (BufferedWriter writer = Files.newBufferedWriter(out, charset)) {
			createPEFileList(writer, startFolder);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			System.out.println("Files read: " + total);
			System.out.println("No PE: " + noPE);
			System.out.println("PE files not loaded: " + notLoaded);
			System.out.println("PE files successfully written: " + written);
		}
	}

	public static void createPEFileList(BufferedWriter writer, File startFolder)
			throws IOException {
		File[] files = startFolder.listFiles();
		if (files == null) {
			System.out.println("Skipped unreadable file: "
					+ startFolder.getCanonicalPath());
			return;
		}
		for (File file : files) {
			total++;
			if (file.isDirectory()) {
				createPEFileList(writer, file);
			} else {
				try {
					new PESignature(file).read();
					String str = file.getAbsolutePath() + "\n";
					writer.write(str, 0, str.length());
					written++;
				} catch (FileFormatException e) {
					noPE++;
				} catch (Exception e) {
					System.err.println(e.getMessage());
					notLoaded++;
				}
				if (total != prevTotal && total % 1000 == 0) {
					prevTotal = total;
					System.out.println("Files read: " + total);
					System.out.println("PE Files read: " + written);
				}
			}
		}
		dirsRead++;
		if (dirsRead % 500 == 0) {
			System.out.println("Directories read: " + dirsRead);
			System.out.println("Current Directory finished: "
					+ startFolder.getAbsolutePath());
		}
	}

	public static void anomalyStats(File[] files) {
		final int ANOMALY_TYPE_NR = AnomalyType.values().length;
		int[] anomalies = new int[ANOMALY_TYPE_NR];
		int[] anPerFile = new int[ANOMALY_TYPE_NR];
		boolean[] occured = new boolean[ANOMALY_TYPE_NR];
		int notLoaded = 0;
		int total = 0;
		for (File file : files) {
			try {
				total++;
				PEData data = PELoader.loadPE(file);
				PEAnomalyScanner scanner = PEAnomalyScanner.newInstance(data);
				List<Anomaly> list = scanner.getAnomalies();
				for (Anomaly a : list) {
					int ordinal = a.getType().ordinal();
					anomalies[ordinal] += 1;
					occured[ordinal] = true;

				}
				for (int i = 0; i < ANOMALY_TYPE_NR; i++) {
					if (occured[i]) {
						anPerFile[i] += 1;
					}
					occured[i] = false;
				}
				if (total % 1000 == 0) {
					System.out.println("Files read: " + total + "/"
							+ files.length);
				}
			} catch (Exception e) {
				logger.error("problem with file " + file.getAbsolutePath()
						+ " file was not loaded!");
				e.printStackTrace();
				notLoaded++;
			}
		}
		double[] averages = new double[ANOMALY_TYPE_NR];
		double[] occPerFile = new double[ANOMALY_TYPE_NR];
		try {
			for (int i = 0; i < ANOMALY_TYPE_NR; i++) {
				averages[i] = anomalies[i] / (double) total;
				occPerFile[i] = anPerFile[i] / (double) total;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		String stats1 = "Averages anomaly count\n\ntotal files: " + total
				+ "\nstructural: " + averages[STRUCTURE.ordinal()]
				+ "\nwrong value: " + averages[WRONG.ordinal()]
				+ "\nreserved: " + averages[RESERVED.ordinal()]
				+ "\ndeprecated: " + averages[DEPRECATED.ordinal()]
				+ "\nnon default: " + averages[NON_DEFAULT.ordinal()];

		String stats2 = "Absolute anomaly count (all files)\n\ntotal files: "
				+ total + "\nstructural: " + anomalies[STRUCTURE.ordinal()]
				+ "\nwrong value: " + anomalies[WRONG.ordinal()]
				+ "\nreserved: " + anomalies[RESERVED.ordinal()]
				+ "\ndeprecated: " + anomalies[DEPRECATED.ordinal()]
				+ "\nnon default: " + anomalies[NON_DEFAULT.ordinal()];

		String stats3 = "Anomaly occurance per file (absolute) \n\ntotal files: "
				+ total
				+ "\nstructural: "
				+ anPerFile[STRUCTURE.ordinal()]
				+ "\nwrong value: "
				+ anPerFile[WRONG.ordinal()]
				+ "\nreserved: "
				+ anPerFile[RESERVED.ordinal()]
				+ "\ndeprecated: "
				+ anPerFile[DEPRECATED.ordinal()]
				+ "\nnon default: " + anPerFile[NON_DEFAULT.ordinal()];

		String stats4 = "Anomaly occurance per file in percent \n\ntotal files: "
				+ total
				+ "\nstructural: "
				+ occPerFile[STRUCTURE.ordinal()]
				+ "\nwrong value: "
				+ occPerFile[WRONG.ordinal()]
				+ "\nreserved: "
				+ occPerFile[RESERVED.ordinal()]
				+ "\ndeprecated: "
				+ occPerFile[DEPRECATED.ordinal()]
				+ "\nnon default: "
				+ occPerFile[NON_DEFAULT.ordinal()]
				+ "\nNot loaded: " + notLoaded + "\nDone\n";
		String report = stats1 + "\n\n" + stats2 + "\n\n" + stats3 + "\n\n"
				+ stats4;
		System.out.println(report);
		writeStats(report);
	}

	//TODO equality of anomalies is nuts, correct it. value differences shouldn't count.
	public static void anomalyCount(File[] files) {
		System.out.println("starting anomaly count");
		Map<Anomaly, Integer> counter = new HashMap<>();
		int filesWUnusualSecNames = 0;
		int filesWCtrlSymbInSec = 0;
		int filesWOverlappingSec = 0;
		int filesWDuplicatedSec = 0;
		int filesWTooLargeSec = 0;
		int filesWZeroVirtSecSize = 0;
		int filesWZeroRawSecSize = 0;
		int total = 0;
		for (File file : files) {
			try {
				total++;
				PEData data = PELoader.loadPE(file);
				PEAnomalyScanner scanner = PEAnomalyScanner.newInstance(data);
				List<Anomaly> list = scanner.getAnomalies();
				for (Anomaly anomaly : list) {
					if (counter.containsKey(anomaly)) {
						int prev = counter.get(anomaly);
						counter.put(anomaly, prev + 1);
					} else {
						counter.put(anomaly, 1);
					}
				}
				if (anyAnomalyContains(list, "Section name is unusual")) {
					filesWUnusualSecNames++;
				}
				if (anyAnomalyContains(list, "has control symbols in name")) {
					filesWCtrlSymbInSec++;
				}
				if (anyAnomalyContains(list, "overlaps with section")) {
					filesWOverlappingSec++;
				}
				if (anyAnomalyContains(list, "is a duplicate of section")) {
					filesWDuplicatedSec++;
				}
				if (anyAnomalyContains(list, "aligned pointer to raw data is larger")) {
					filesWTooLargeSec++;
				}
				if (anyAnomalyContains(list, "VIRTUAL_SIZE is 0")) {
					filesWZeroVirtSecSize++;
				}
				if (anyAnomalyContains(list, "SIZE_OF_RAW_DATA is 0")) {
					filesWZeroRawSecSize++;
				}
				if (total % 1000 == 0) {
					System.out.println("Files read: " + total + "/"
							+ files.length);
				}
			} catch (Exception e) {
				logger.error("problem with file " + file.getAbsolutePath()
						+ " file was not loaded!");
				e.printStackTrace();
				notLoaded++;
			}
		}
		String preamble = "Files with Anomalies Counted: \n\n"
				+ "section(s) with zero virtual size: " + filesWZeroVirtSecSize
				+ "\nsection(s) with zero raw size: " + filesWZeroRawSecSize
				+ "\nsection(s) with unusual names: " + filesWUnusualSecNames
				+ "\nsection(s) with control symbols in names: "
				+ filesWCtrlSymbInSec + "\nsection(s) too large: "
				+ filesWTooLargeSec + "\nsections duplicated: "
				+ filesWDuplicatedSec + "\nsections overlapping: "
				+ filesWOverlappingSec;
		String report = preamble + "\n\nAnomalies Counted: \n\n"
				+ createReport(counter) + "\n total files: " + total
				+ "\n not loaded: " + notLoaded + "\nDone\n\n";
		System.out.println(report);
		writeStats(report);
		System.out.println("anomaly count done");
	}

	private static boolean anyAnomalyContains(List<Anomaly> list,
			String description) {
		for (Anomaly anomaly : list) {
			if (anomaly.description().contains(description)) {
				return true;
			}
		}
		return false;
	}

	private static String createReport(Map<Anomaly, Integer> map) {
		StringBuilder b = new StringBuilder();
		for (Entry<Anomaly, Integer> entry : map.entrySet()) {
			Anomaly anomaly = entry.getKey();
			Integer counter = entry.getValue();
			b.append(counter + " times " + anomaly.getType() + " "
					+ anomaly.description() + "\n");
		}
		return b.toString();
	}

	public static void overlayPrevalence(File[] files) {
		int hasOverlay = 0;
		int hasNoOverlay = 0;
		int notLoaded = 0;
		int total = 0;
		for (File file : files) {
			try {
				total++;
				PEData data = PELoader.loadPE(file);
				Overlay overlay = new Overlay(data);
				if (overlay.exists()) {
					hasOverlay++;
				} else {
					hasNoOverlay++;
				}
				if (total % 1000 == 0) {
					System.out.println("Files read: " + total + "/"
							+ files.length);
				}
			} catch (Exception e) {
				logger.error(e);
				notLoaded++;
			}
		}
		double percentage = total / (double) hasOverlay;
		String stats = "total: " + total + "\nhas overlay: " + hasOverlay
				+ "\nno overlay: " + hasNoOverlay
				+ "\npercentage files with overlay: " + percentage
				+ "\nNot loaded: " + notLoaded + "\nDone\n";
		System.out.println(stats);
		writeStats(stats);
	}

	public static void fileTypeCount(File[] files) {
		int dllCount = 0;
		int pe32PlusCount = 0;
		int pe32Count = 0;
		int sysCount = 0;
		int exeCount = 0;
		int notLoaded = 0;
		int total = 0;
		for (File file : files) {
			try {
				total++;
				PEData data = PELoader.loadPE(file);
				COFFFileHeader coff = data.getCOFFFileHeader();
				if (coff.hasCharacteristic(FileCharacteristic.IMAGE_FILE_DLL)) {
					dllCount++;
				}
				if (coff.hasCharacteristic(FileCharacteristic.IMAGE_FILE_SYSTEM)) {
					sysCount++;
				}
				if (coff.hasCharacteristic(FileCharacteristic.IMAGE_FILE_EXECUTABLE_IMAGE)) {
					exeCount++;
				}
				MagicNumber magic = data.getOptionalHeader().getMagicNumber();
				if (magic.equals(MagicNumber.PE32)) {
					pe32Count++;
				}
				if (magic.equals(MagicNumber.PE32_PLUS)) {
					pe32PlusCount++;
				}
				if (total % 1000 == 0) {
					System.out.println("Files read: " + total + "/"
							+ files.length);
				}
			} catch (Exception e) {
				logger.error(e);
				notLoaded++;
			}
		}
		String stats = "total: " + total + "\nPE32 files: " + pe32Count
				+ "\nPE32+ files: " + pe32PlusCount + "\nDLL files: "
				+ dllCount + "\nSystem files: " + sysCount + "\nExe files: "
				+ exeCount + "\nNot loaded: " + notLoaded + "\nDone\n";
		System.out.println(stats);
		writeStats(stats);
	}

	private static void writeStats(String stats) {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"yyyy-MM-dd_HH-mm-ss");
		String filename = dateFormat.format(date) + ".stat";
		Path path = Paths.get(STATS_FOLDER, filename);
		writeToFile(path, stats);
		System.out.println("stats written to " + filename);
	}

	private static void writeToFile(Path path, String str) {
		Charset charset = Charset.forName("UTF-8");
		try (BufferedWriter writer = Files.newBufferedWriter(path, charset)) {
			writer.write(str, 0, str.length());
		} catch (IOException x) {
			logger.error(x);
		}
	}

	public static int ableToLoadSections() {
		int ableToLoad = 0;
		int unableToLoad = 0;
		int filesReadCounter = 0;
		File folder = new File(PE_FOLDER);
		File[] files = folder.listFiles();
		for (File file : files) {
			try {
				PEData data = PELoader.loadPE(file);
				SectionLoader loader = new SectionLoader(data);
				Map<DataDirectoryKey, DataDirEntry> map = data
						.getOptionalHeader().getDataDirEntries();
				if (map.containsKey(DataDirectoryKey.RESOURCE_TABLE)) {
					loader.loadResourceSection();
				}
				if (map.containsKey(DataDirectoryKey.IMPORT_TABLE)) {
					loader.loadImportSection();
				}
				if (map.containsKey(DataDirectoryKey.EXPORT_TABLE)) {
					loader.loadExportSection();
				}
				ableToLoad++;
			} catch (Exception e) {
				System.out.println(e.getMessage());
				unableToLoad++;
			}
			filesReadCounter++;
			if (filesReadCounter % 100 == 0) {
				System.out.println("Files read: " + filesReadCounter);
				System.out.println("Able to load: " + ableToLoad);
				System.out.println("Unable to load: " + unableToLoad);
				System.out.println();
			}
		}
		System.out.println("Files read: " + filesReadCounter);
		System.out.println("Able to load: " + ableToLoad);
		System.out.println("Unable to load: " + unableToLoad);
		return ableToLoad;
	}

	public static int ableToLoad() {
		int ableToLoad = 0;
		int unableToLoad = 0;
		int filesReadCounter = 0;
		File folder = new File(PE_FOLDER);
		File[] files = folder.listFiles();
		for (File file : files) {
			try {
				PELoader.loadPE(file);
				ableToLoad++;
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
				unableToLoad++;
			}
			filesReadCounter++;
			if (filesReadCounter % 100 == 0) {
				System.out.println("Files read: " + filesReadCounter);
				System.out.println("Able to load: " + ableToLoad);
				System.out.println("Unable to load: " + unableToLoad);
				System.out.println();
			}
		}
		System.out.println("Files read: " + filesReadCounter);
		System.out.println("Able to load: " + ableToLoad);
		System.out.println("Unable to load: " + unableToLoad);
		return ableToLoad;
	}

	public static void sortPEFiles() throws IOException {
		File folder = new File(BASE_MALW_FOLDER);
		int peCount = 0;
		int noPECount = 0;
		int filesReadCounter = 0;
		System.out.println("reading ...");
		for (File file : folder.listFiles()) {
			if (file.isDirectory())
				continue;
			try {
				PESignature signature = new PESignature(file);
				signature.read();
				peCount++;
				file.renameTo(new File(PE_FOLDER + file.getName()));
			} catch (FileFormatException e) {
				noPECount++;
				file.renameTo(new File(NO_PE_FOLDER + file.getName()));
			}
			filesReadCounter++;
			if (filesReadCounter % 100 == 0) {
				System.out.println("Files read: " + filesReadCounter);
				System.out.println("PEs found: " + peCount);
				System.out.println("No PEs: " + noPECount);
				System.out.println();
			}
		}
		System.out.println("PEs found: " + peCount);
		System.out.println("No PEs: " + noPECount);
	}
}
