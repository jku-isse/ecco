package at.jku.isse.ecco.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Whether a directory is in a folder a cloud service synchronizes - iCloud Drive (also the Desktop and
 * Documents folders when macOS syncs them), Dropbox, OneDrive, Google Drive. Such folders can evict a
 * file's content and fetch it again on access, and reading the repository then fails with "Operation
 * timed out"; their file locks are less reliable, and they can upload a repository while it is being
 * written. Detected by location: macOS keeps these folders in known places, and its file-provider
 * attributes are not visible to Java.
 */
public final class SyncedFolders {

	private SyncedFolders() {
	}

	/** The service that synchronizes {@code dir}, if any. */
	public static Optional<String> provider(Path dir) {
		return provider(dir, Path.of(System.getProperty("user.home")));
	}

	static Optional<String> provider(Path dir, Path home) {
		Path path = real(dir);
		home = real(home);
		Path icloud = home.resolve("Library").resolve("Mobile Documents");
		if (path.startsWith(icloud))
			return Optional.of("iCloud Drive");
		Path cloudStorage = home.resolve("Library").resolve("CloudStorage");
		if (path.startsWith(cloudStorage) && path.getNameCount() > cloudStorage.getNameCount())
			return Optional.of(providerName(path.getName(cloudStorage.getNameCount()).toString()));
		for (String folder : new String[]{"Desktop", "Documents"})
			if (path.startsWith(home.resolve(folder)) && Files.isDirectory(icloud.resolve("com~apple~CloudDocs").resolve(folder)))
				return Optional.of("iCloud Drive (" + folder + " is synced)");
		for (Path segment : path) {
			String name = segment.toString();
			if (name.equals("Dropbox") || name.startsWith("Dropbox ("))
				return Optional.of("Dropbox");
			if (name.startsWith("OneDrive"))
				return Optional.of("OneDrive");
			if (name.equals("Google Drive") || name.equals("My Drive"))
				return Optional.of("Google Drive");
			if (name.equals("iCloudDrive"))
				return Optional.of("iCloud Drive");
		}
		return Optional.empty();
	}

	/** "OneDrive-Personal" -> "OneDrive", "GoogleDrive-me@x.org" -> "Google Drive". */
	private static String providerName(String folder) {
		String name = folder.contains("-") ? folder.substring(0, folder.indexOf('-')) : folder;
		return name.equals("GoogleDrive") ? "Google Drive" : name;
	}

	private static Path real(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		// the nearest existing ancestor's real path: the repository directory may not exist yet (init)
		for (Path existing = absolute; existing != null; existing = existing.getParent()) {
			try {
				return existing.toRealPath().resolve(existing.relativize(absolute));
			} catch (IOException e) {
				// not there (yet): try its parent
			}
		}
		return absolute;
	}

	/** A warning for a repository at {@code repositoryDir}, if it is in a synced folder. */
	public static Optional<String> warning(Path repositoryDir) {
		return provider(repositoryDir).map(provider -> "The repository " + repositoryDir.toAbsolutePath().normalize() + " is in a folder synchronized by " + provider
				+ ". Synchronized folders can remove a file's content from the disk and fail to fetch it in time (\"Operation timed out\"),"
				+ " and can synchronize a repository while it is being written. Keep ECCO repositories outside synchronized folders,"
				+ " or keep the folder downloaded (Finder: Keep Downloaded).");
	}
}
