package com.arcticlauncher.polarium.pack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** The pack owns this handle and closes it through vanilla's normal lifecycle. */
public final class IndexedPackZip extends ZipFile {
	private static final int DIRECTORY_DEPTH = 4;
	private static final ZipEntry AMBIGUOUS = new ZipEntry("");
	public static final boolean ENABLED = !Boolean.getBoolean("polarium.off")
			&& !"false".equalsIgnoreCase(System.getProperty("polarium.zipIndex"));

	private final boolean indexDirectories;
	private volatile Index index;

	public IndexedPackZip(File file, boolean indexDirectories) throws IOException {
		super(file);
		this.indexDirectories = indexDirectories;
	}

	@Override
	public ZipEntry getEntry(String name) {
		Index snapshot = index;
		if (snapshot != null && !snapshot.duplicates && name != null) {
			ZipEntry entry = snapshot.names.get(name);
			if (entry != null && entry != AMBIGUOUS) {
				return entry;
			}
		}
		return super.getEntry(name);
	}

	/** Lazily index at the first scan, so a metadata-only open remains cheap. */
	private synchronized Index index() {
		if (index == null) {
			index = new Index(super.entries(), indexDirectories, size());
		}
		return index;
	}

	public Enumeration<? extends ZipEntry> under(String prefix) {
		Index snapshot = index();
		// JDKs may use the last enumerated entry to resolve duplicate names when opening streams.
		return snapshot.duplicates ? super.entries() : Collections.enumeration(snapshot.under(prefix));
	}

	@Override
	public synchronized void close() throws IOException {
		index = null;
		super.close();
	}

	/**
	 * Vanilla inserts each valid namespace into a set. Pass through the first
	 * occurrence and retain unusual names so vanilla still validates and logs them.
	 */
	public Enumeration<? extends ZipEntry> namespaces(String prefix) {
		Index snapshot = index();
		if (snapshot.duplicates) {
			return super.entries();
		}
		return Collections.enumeration(snapshot.namespaces.computeIfAbsent(prefix,
				key -> namespaceCandidates(snapshot.under(key), key)));
	}

	private static List<ZipEntry> namespaceCandidates(List<ZipEntry> entries, String prefix) {
		List<ZipEntry> candidates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String previous = null;
		int start = prefix.length();
		for (ZipEntry entry : entries) {
			String name = entry.getName();
			int end = name.indexOf('/', start);
			if (end < 0) {
				end = name.length();
			}
			if (previous != null && previous.length() == end - start
					&& name.regionMatches(start, previous, 0, previous.length())) {
				continue;
			}
			String namespace = name.substring(start, end);
			if (!plainNamespace(namespace)) {
				candidates.add(entry);
			} else {
				previous = namespace;
				if (seen.add(namespace)) {
					candidates.add(entry);
				}
			}
		}
		return candidates;
	}

	private static boolean plainNamespace(String namespace) {
		for (int i = 0; i < namespace.length(); i++) {
			char c = namespace.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-')) {
				return false;
			}
		}
		return !namespace.isEmpty();
	}

	private static final class Index {
		private final Map<String, ZipEntry> names;
		private final Map<String, List<ZipEntry>> directories = new HashMap<>();
		private final Map<String, List<ZipEntry>> namespaces = new ConcurrentHashMap<>();
		private final boolean duplicates;

		private Index(Enumeration<? extends ZipEntry> entries, boolean indexDirectories, int size) {
			names = new HashMap<>((int) (size / 0.75f) + 1);
			List<String> directoryAliases = new ArrayList<>();
			String[] lastPaths = new String[DIRECTORY_DEPTH];
			List<List<ZipEntry>> lastBuckets = new ArrayList<>(Collections.nCopies(DIRECTORY_DEPTH, null));
			boolean hasDuplicates = false;
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (names.putIfAbsent(name, entry) != null) {
					names.put(name, AMBIGUOUS);
					hasDuplicates = true;
				}
				if (entry.isDirectory()) {
					directoryAliases.add(name.substring(0, name.length() - 1));
				}
				if (!indexDirectories) {
					if (name.startsWith("assets/")) {
						directories.computeIfAbsent("assets/", key -> new ArrayList<>()).add(entry);
					}
					continue;
				}
				int end = 0;
				for (int depth = 0; depth < DIRECTORY_DEPTH; depth++) {
					end = name.indexOf('/', end) + 1;
					if (end == 0) {
						break;
					}
					String previous = lastPaths[depth];
					if (previous == null || previous.length() != end || !name.startsWith(previous)) {
						String directory = name.substring(0, end);
						lastPaths[depth] = directory;
						lastBuckets.set(depth, directories.computeIfAbsent(directory, key -> new ArrayList<>()));
					}
					lastBuckets.get(depth).add(entry);
				}
			}
			// The JDK chooses between an exact name and its slash-terminated alias.
			for (String alias : directoryAliases) {
				names.put(alias, AMBIGUOUS);
			}
			duplicates = hasDuplicates;
		}

		private List<ZipEntry> under(String prefix) {
			int end = 0;
			for (int depth = 0; depth < DIRECTORY_DEPTH; depth++) {
				end = prefix.indexOf('/', end) + 1;
				if (end == 0 || end == prefix.length()) {
					return directories.getOrDefault(prefix, Collections.emptyList());
				}
			}
			List<ZipEntry> matches = new ArrayList<>();
			for (ZipEntry entry : directories.getOrDefault(prefix.substring(0, end), Collections.emptyList())) {
				if (entry.getName().startsWith(prefix)) {
					matches.add(entry);
				}
			}
			return matches;
		}
	}
}
