package at.jku.isse.ecco.plugin.artifact.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public class FileArtifactData {

	private static byte[] getSHADigest(Path path) throws IOException, NoSuchAlgorithmException {
		//InputStream fis = new FileInputStream(path.toFile());
		InputStream fis = Files.newInputStream(path);
		byte[] buffer = new byte[1024];
		MessageDigest complete = MessageDigest.getInstance("SHA1");
		int numRead;
		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);
		fis.close();
		return complete.digest();
	}

	private static final String HEXES = "0123456789ABCDEF";

	private static String getHex(byte[] raw) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}

	private static byte[] getData(Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	// #########################################################

	private byte[] checksum;
	private String hexChecksum;
	private byte[] data;
	private Path path;

	public FileArtifactData(Path base, Path path) throws IOException {
		//this.path = file.toPath().relativize(new File(".").toPath());
		//this.path = base.relativize(path);
		this.path = path;
		Path resolvedPath = base.resolve(path);
		try {
			this.checksum = FileArtifactData.getSHADigest(resolvedPath);
			this.hexChecksum = FileArtifactData.getHex(this.checksum);
		} catch (NoSuchAlgorithmException e) {
			this.checksum = null;
			this.hexChecksum = null;
		}
		this.data = FileArtifactData.getData(resolvedPath);
	}

	public byte[] getChecksum() {
		return this.checksum;
	}

	public String getHexChecksum() {
		return this.hexChecksum;
	}

	public Path getPath() {
		return this.path;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final FileArtifactData other = (FileArtifactData) obj;
		return Arrays.equals(this.checksum, other.checksum);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.checksum);
	}

	public byte[] getData() {
		return data;
	}

	public String getIdentifier() {
		return this.hexChecksum;
	}

}
