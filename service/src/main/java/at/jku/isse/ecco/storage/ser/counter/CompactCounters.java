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

	private CompactCounters() {
	}

	/** The stream association files are written with - counters written to it are encoded. */
	public static final class Stream extends ObjectOutputStream {
		public Stream(OutputStream out) throws IOException {
			super(out);
		}
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
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			int version = readVarint(in);
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
