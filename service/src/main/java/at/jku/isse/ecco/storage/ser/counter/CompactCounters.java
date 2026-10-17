package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.eclipse.collections.impl.factory.Maps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact encoding of an association's counters in its association file.
 * <p>
 * Java serialization wrote a copy of every counted module and module revision - with its feature
 * arrays and the module's whole revision list - only for the load to replace each copy by the
 * repository's own instance (SerTransactionStrategy.resolveModuleReferences). The counters are
 * almost all of an association file, and there are many: about features^3 per association with
 * the default maximum order of 2, e.g. 1.1 million in a repository of 60 variants over 40
 * features, at about 130 bytes each. Encoded as ids into per-file tables of features and feature
 * revisions, plus counts, an entry takes about 10 bytes.
 * <p>
 * Used only for the association files ({@link Stream}): anything else that serializes an
 * association (e.g. remote synchronization) gets the plain form, which needs no repository to
 * resolve against.
 */
public final class CompactCounters {

	private static final int VERSION = 1;
	/**
	 * Version 2: the counted module revisions as indices into the repository's list of them
	 * (SerRepository#countedIndex, in the core), sorted, each written as the gap to the one before
	 * - in a repository of 42 features an association counts up to 36,000 module revisions, most of
	 * those the repository has, and version 1 wrote each one's features again in every association
	 * file (16.6 MB for 53 associations).
	 */
	private static final int VERSION_INDEXED = 2;

	private CompactCounters() {
	}

	/** The stream association files are written with - counters written to it are encoded. */
	public static class Stream extends ObjectOutputStream {
		// the repository whose list of counted module revisions the counters refer to (version 2); null: version 1
		private final SerRepository repository;

		public Stream(OutputStream out) throws IOException {
			this(out, null);
		}

		public Stream(OutputStream out, SerRepository repository) throws IOException {
			super(out);
			this.repository = repository;
		}

		SerRepository repository() {
			return this.repository;
		}
	}

	/** [moduleCounters] encoded for association files: version 2 with [repository], else version 1. */
	static byte[] encode(Collection<SerModuleCounter> moduleCounters, SerRepository repository) throws IOException {
		if (repository == null || moduleCounters.stream().anyMatch(counter -> counter.getChildren().isEmpty()))
			return encode(moduleCounters); // (a module counted without a revision: version 1 writes it)
		// each counted revision: its index, its count, and its module's count beyond it - written for
		// the first of a module's revisions only
		int size = 0;
		for (SerModuleCounter moduleCounter : moduleCounters)
			size += moduleCounter.getChildren().size();
		long[] entries = new long[size]; // index << 32 | position in the lists below
		int[] counts = new int[size], moduleCounts = new int[size];
		int e = 0;
		for (SerModuleCounter moduleCounter : moduleCounters) {
			boolean first = true;
			for (ModuleRevisionCounter revisionCounter : moduleCounter.getChildren()) {
				int index = repository.countedIndex((SerModuleRevision) revisionCounter.getObject());
				entries[e] = ((long) index << 32) | e;
				counts[e] = revisionCounter.getCount();
				moduleCounts[e] = first ? moduleCounter.getCount() : -1;
				first = false;
				e++;
			}
		}
		java.util.Arrays.sort(entries);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		writeVarint(out, VERSION_INDEXED);
		writeVarint(out, size);
		int previous = -1;
		for (long entry : entries) {
			int index = (int) (entry >>> 32), position = (int) entry;
			writeVarint(out, index - previous - 1);
			previous = index;
			writeVarint(out, counts[position]);
			// the module's count: 0 for none here (another of its revisions carries it), else 1 + count
			writeVarint(out, moduleCounts[position] < 0 ? 0 : moduleCounts[position] + 1);
		}
		out.flush();
		return bytes.toByteArray();
	}

	static byte[] encode(Collection<SerModuleCounter> moduleCounters) throws IOException {
		Map<String, Integer> features = new LinkedHashMap<>();
		Map<FeatureRevision, Integer> revisions = new LinkedHashMap<>();
		List<FeatureRevision> revisionList = new ArrayList<>();

		ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		DataOutputStream body = new DataOutputStream(bodyBytes);
		writeVarint(body, moduleCounters.size());
		for (SerModuleCounter moduleCounter : moduleCounters) {
			Module module = moduleCounter.getObject();
			writeFeatures(body, module.getPos(), features);
			writeFeatures(body, module.getNeg(), features);
			writeVarint(body, moduleCounter.getCount());
			Collection<ModuleRevisionCounter> revisionCounters = moduleCounter.getChildren();
			writeVarint(body, revisionCounters.size());
			for (ModuleRevisionCounter revisionCounter : revisionCounters) {
				SerModuleRevision moduleRevision = (SerModuleRevision) revisionCounter.getObject();
				FeatureRevision[] pos = moduleRevision.getPos();
				writeVarint(body, pos.length);
				for (FeatureRevision featureRevision : pos) {
					Integer index = revisions.get(featureRevision);
					if (index == null) {
						index = revisions.size();
						revisions.put(featureRevision, index);
						revisionList.add(featureRevision);
						features.computeIfAbsent(featureRevision.getFeature().getId(), id -> features.size());
					}
					writeVarint(body, index);
				}
				// a revision's negative features have always been its module's - only a difference is written
				boolean sameNeg = Arrays.equals(moduleRevision.getNeg(), module.getNeg());
				body.writeBoolean(sameNeg);
				if (!sameNeg)
					writeFeatures(body, moduleRevision.getNeg(), features);
				writeVarint(body, revisionCounter.getCount());
			}
		}

		ByteArrayOutputStream allBytes = new ByteArrayOutputStream();
		DataOutputStream all = new DataOutputStream(allBytes);
		writeVarint(all, VERSION);
		writeVarint(all, features.size());
		for (String featureId : features.keySet())
			all.writeUTF(featureId);
		writeVarint(all, revisionList.size());
		for (FeatureRevision featureRevision : revisionList) {
			writeVarint(all, features.get(featureRevision.getFeature().getId()));
			all.writeUTF(featureRevision.getId());
		}
		body.flush();
		bodyBytes.writeTo(all);
		all.flush();
		return allBytes.toByteArray();
	}

	/**
	 * The counters encoded in {@code data}, on the repository's own features, modules and module
	 * revisions. All of them must exist: the counters are what they were built from.
	 */
	static Map<Module, SerModuleCounter> decode(byte[] data, SerRepository repository, String associationId) {
		return decode(data, repository, associationId, new Resolved(repository));
	}

	/**
	 * The repository's own module revisions for the indices counters refer to, each looked up once:
	 * it was looked up for every counter - 17.8 million times for 9,309 module revisions, most of the
	 * 4.8 s decoding tinycc's counters took. For the decoding of one load; the repository does not
	 * change meanwhile.
	 */
	public static final class Resolved {
		private final SerRepository repository;
		private SerModuleRevision[] revisions = new SerModuleRevision[0];
		private SerModule[] modules = new SerModule[0];

		public Resolved(SerRepository repository) {
			this.repository = repository;
		}

		/** The repository's own module revision counted by [index]; null if it has none. */
		private SerModuleRevision revision(int index) {
			if (index < this.revisions.length && this.revisions[index] != null)
				return this.revisions[index];
			SerModuleRevision listed = this.repository.countedRevision(index);
			if (listed == null)
				return null;
			// the repository's own instances (the list holds them, but resolve as version 1 does)
			Module listedModule = listed.getModule();
			SerModule module = this.repository.getModule(listedModule.getPos(), listedModule.getNeg());
			SerModuleRevision moduleRevision = module == null ? null : module.getRevision(listed.getPos(), listed.getNeg());
			if (moduleRevision == null)
				return null;
			if (index >= this.revisions.length) {
				this.revisions = Arrays.copyOf(this.revisions, Math.max(index + 1, this.revisions.length * 2));
				this.modules = Arrays.copyOf(this.modules, this.revisions.length);
			}
			this.revisions[index] = moduleRevision;
			this.modules[index] = module;
			return moduleRevision;
		}

		/** The repository's own module of {@link #revision}([index]), after that was found. */
		private SerModule module(int index) {
			return this.modules[index];
		}
	}

	static Map<Module, SerModuleCounter> decode(byte[] data, SerRepository repository, String associationId, Resolved resolved) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			int version = readVarint(in);
			if (version == VERSION_INDEXED)
				return decodeIndexed(in, resolved, associationId);
			if (version != VERSION)
				throw new EccoException("Association " + associationId + " has counters in an unknown format (version " + version + "), written by a newer version of ECCO.");

			Feature[] features = new Feature[readVarint(in)];
			for (int i = 0; i < features.length; i++) {
				String featureId = in.readUTF();
				features[i] = repository.getFeature(featureId);
				if (features[i] == null)
					throw damaged(associationId, "feature " + featureId);
			}
			FeatureRevision[] revisions = new FeatureRevision[readVarint(in)];
			for (int i = 0; i < revisions.length; i++) {
				Feature feature = features[readVarint(in)];
				String revisionId = in.readUTF();
				revisions[i] = feature.getRevision(revisionId);
				if (revisions[i] == null)
					throw damaged(associationId, "feature revision " + feature.getName() + "." + revisionId);
			}

			Map<Module, SerModuleCounter> children = Maps.mutable.empty();
			int moduleCount = readVarint(in);
			for (int m = 0; m < moduleCount; m++) {
				Feature[] pos = readFeatures(in, features);
				Feature[] neg = readFeatures(in, features);
				SerModule module = repository.getModule(pos, neg);
				if (module == null)
					throw damaged(associationId, "module " + Arrays.toString(pos) + " / not " + Arrays.toString(neg));
				SerModuleCounter moduleCounter = new SerModuleCounter(module);
				moduleCounter.setCount(readVarint(in));

				int revisionCount = readVarint(in);
				for (int r = 0; r < revisionCount; r++) {
					FeatureRevision[] revisionPos = new FeatureRevision[readVarint(in)];
					for (int i = 0; i < revisionPos.length; i++)
						revisionPos[i] = revisions[readVarint(in)];
					Feature[] revisionNeg = in.readBoolean() ? neg : readFeatures(in, features);
					SerModuleRevision moduleRevision = module.getRevision(revisionPos, revisionNeg);
					if (moduleRevision == null)
						throw damaged(associationId, "module revision " + Arrays.toString(revisionPos) + " / not " + Arrays.toString(revisionNeg));
					moduleCounter.addChild(moduleRevision).setCount(readVarint(in));
				}
				children.put(module, moduleCounter);
			}
			if (in.available() != 0)
				throw new EccoException("Association " + associationId + " has damaged counters: unexpected data after them.");
			return children;
		} catch (IOException e) {
			throw new EccoException("Association " + associationId + " has damaged counters.", e);
		}
	}

	/** Version 2 - see {@link #VERSION_INDEXED}. */
	private static Map<Module, SerModuleCounter> decodeIndexed(DataInputStream in, Resolved resolved, String associationId) throws IOException {
		Map<Module, SerModuleCounter> children = Maps.mutable.empty();
		int size = readVarint(in);
		int index = -1;
		for (int e = 0; e < size; e++) {
			index += readVarint(in) + 1;
			int count = readVarint(in), moduleCount = readVarint(in);
			SerModuleRevision moduleRevision = resolved.revision(index);
			if (moduleRevision == null)
				throw damaged(associationId, resolved.repository.countedRevision(index) == null ? "module revision #" + index : "module revision " + resolved.repository.countedRevision(index));
			SerModule module = resolved.module(index);
			SerModuleCounter moduleCounter = children.computeIfAbsent(module, m -> new SerModuleCounter(module));
			if (moduleCount > 0)
				moduleCounter.setCount(moduleCount - 1);
			moduleCounter.addChild(moduleRevision).setCount(count);
		}
		if (in.available() != 0)
			throw new EccoException("Association " + associationId + " has damaged counters: unexpected data after them.");
		return children;
	}

	private static EccoException damaged(String associationId, String what) {
		return new EccoException("Association " + associationId + " counts " + what + ", which the repository does not contain - the repository is damaged.");
	}

	private static void writeFeatures(DataOutputStream out, Feature[] features, Map<String, Integer> table) throws IOException {
		writeVarint(out, features.length);
		for (Feature feature : features)
			writeVarint(out, table.computeIfAbsent(feature.getId(), id -> table.size()));
	}

	private static Feature[] readFeatures(DataInputStream in, Feature[] table) throws IOException {
		Feature[] features = new Feature[readVarint(in)];
		for (int i = 0; i < features.length; i++)
			features[i] = table[readVarint(in)];
		return features;
	}

	private static void writeVarint(DataOutputStream out, int value) throws IOException {
		if (value < 0)
			throw new IllegalArgumentException("negative: " + value);
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarint(DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0)
				return value;
		}
		throw new IOException("malformed varint");
	}
}
