package at.jku.isse.ecco.cli.benchmark;

import at.jku.isse.ecco.test.TestJvm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The performance limits of doc/requirements.md that hold on generated repositories (NFR-P1a-c,
 * NFR-P3, NFR-P4a/b, NFR-P5), checked as a user meets them: each step is an {@code ecco} command
 * in a JVM of its own, with the shipped CLI's memory settings, timed from start to exit - the
 * median of {@value #RUNS} runs, each on a copy of the same repository. The repositories are
 * generated here, from fixed seeds, so every run builds the same ones.
 * <p>
 * The limits are one and a half times the baselines this benchmark recorded on 2026-11-09 on the
 * development Mac the requirements name (rounded up to a tenth): a slower machine can fail them
 * without a regression. A step's time includes its JVM's start, about a quarter of a second. Not part of {@code test}
 * or {@code integrationTest}: {@code ./gradlew :ecco-cli:benchmark}, before a release. Each result
 * goes to the console and to {@code build/benchmark/results.txt}.
 */
@Tag("benchmark")
public class PerformanceBenchmark {

	static final int RUNS = 3;

	/** As the shipped CLI runs (cli/build.gradle), without the checks the tests turn on. */
	private static final List<String> JVM_OPTIONS = List.of("-Xmx8g", "-Xss1024m",
			"-Decco.verifyUnchangedArtifacts=false", "-Decco.verifyUnchangedAssociations=false");

	private static final List<String> results = new ArrayList<>();
	private static final List<String> exceeded = new ArrayList<>();

	@BeforeAll
	public static void clearResults() {
		results.clear();
		exceeded.clear();
	}

	@AfterAll
	public static void writeResults() throws IOException {
		Path file = Path.of("build", "benchmark", "results.txt");
		Files.createDirectories(file.getParent());
		Files.write(file, results);
	}

	// --- NFR-P1a-c, NFR-P5: 60 variants over 40 features ---

	static final int FEATURES = 40;
	static final int VARIANTS = 60;

	@Test
	public void variantsOverFeatures(@TempDir Path tmp) throws Exception {
		List<String> configurations = FeatureVariants.configurations();
		Path work = Files.createDirectories(tmp.resolve("variants"));
		ok(work, "init");
		for (int v = 0; v < VARIANTS - 1; v++)
			commit(work, FeatureVariants.variant(configurations.get(v)), configurations.get(v));

		// the 60th variant, committed into copies of the repository of the first 59
		String last = configurations.get(VARIANTS - 1);
		double commit = median(copies(work, tmp, "commit"), copy -> {
			writeVariant(copy, FeatureVariants.variant(last));
			return timed(copy, "commit", "-c", last, "-m", "variant " + VARIANTS);
		});
		commit(work, FeatureVariants.variant(last), last);

		String middle = configurations.get(VARIANTS / 2);
		double checkout = median(copies(work, tmp, "checkout"), copy -> {
			clear(copy);
			return timed(copy, "checkout", "-c", middle);
		});
		double status = median(copies(work, tmp, "status"), copy -> timed(copy, "status"));
		long associations = size(work.resolve(".ecco"), "associations");

		check("NFR-P1a", "commit of the 60th variant", commit, 3.1);
		check("NFR-P1b", "checkout of a configuration", checkout, 2.1);
		check("NFR-P1c", "status", status, 0.5);
		check("NFR-P5", "associations after 60 commits, MB", associations / 1e6, 4.7);
		assertWithinLimits();
	}

	// --- NFR-P3: a tenth of the files of a large repository edited ---

	@Test
	public void editATenthOfTheFiles(@TempDir Path tmp) throws Exception {
		Path work = Files.createDirectories(tmp.resolve("large"));
		ok(work, "init");
		commit(work, LargeRepository.files(0), "BASE");
		double commit = median(copies(work, tmp, "edit"), copy -> {
			writeVariant(copy, LargeRepository.files(1));
			return timed(copy, "commit", "-c", "BASE", "-m", "a tenth edited");
		});
		check("NFR-P3", "commit editing a tenth of the files", commit, 1.3);
		assertWithinLimits();
	}

	// --- NFR-P4a/b: an image of 71,289 pixels ---

	@Test
	public void image(@TempDir Path tmp) throws Exception {
		Path work = Files.createDirectories(tmp.resolve("image"));
		ok(work, "init");
		Path empty = Files.createDirectories(tmp.resolve("image-empty"));
		ok(empty, "init");
		double commit = median(copies(empty, tmp, "image-commit"), copy -> {
			writeImage(copy);
			return timed(copy, "commit", "-c", "BASE", "-m", "image");
		});
		writeImage(work);
		ok(work, "commit", "-c", "BASE", "-m", "image");
		double checkout = median(copies(work, tmp, "image-checkout"), copy -> {
			clear(copy);
			double seconds = timed(copy, "checkout", "-c", "BASE");
			assertTrue(Files.size(copy.resolve("image.png")) > 0);
			return seconds;
		});
		check("NFR-P4a", "commit of the image", commit, 3.1);
		check("NFR-P4b", "checkout of the image", checkout, 1.9);
		assertWithinLimits();
	}

	/** 267 x 267 pixels, a gradient with noise from a fixed seed: few pixels alike. */
	static void writeImage(Path work) throws IOException {
		BufferedImage image = new BufferedImage(267, 267, BufferedImage.TYPE_INT_RGB);
		Random random = new Random(71289);
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				image.setRGB(x, y, ((x + random.nextInt(32)) & 0xff) << 16 | ((y + random.nextInt(32)) & 0xff) << 8 | random.nextInt(256));
		ImageIO.write(image, "png", work.resolve("image.png").toFile());
	}

	// --- the repositories ---

	/**
	 * 60 variants over 40 features: each has BASE and each feature with a probability of 0.3. Its
	 * files are 20 shared files of 50 lines each, into which every feature selected puts a block of
	 * 5 lines at a place of its own (in 3 of the files), and 30 fixed pairs of features a line of
	 * their interaction where both are selected; and a file of 20 lines per feature selected.
	 */
	static final class FeatureVariants {

		static List<String> configurations() {
			Random random = new Random(4060);
			List<String> configurations = new ArrayList<>();
			for (int v = 0; v < VARIANTS; v++) {
				List<String> features = new ArrayList<>(List.of("BASE"));
				for (int f = 1; f <= FEATURES; f++)
					if (random.nextDouble() < 0.3)
						features.add(feature(f));
				configurations.add(String.join(", ", features));
			}
			return configurations;
		}

		static String feature(int f) {
			return String.format("f%02d", f);
		}

		static List<FileContent> variant(String configuration) {
			List<String> selected = Arrays.asList(configuration.split(",\\s*"));
			List<FileContent> files = new ArrayList<>();
			for (int file = 0; file < 20; file++) {
				StringBuilder text = new StringBuilder();
				for (int line = 0; line < 50; line++) {
					text.append("shared ").append(file).append(" line ").append(line).append('\n');
					for (int f = 1; f <= FEATURES; f++) {
						// feature f's block: in files f, f + 7 and f + 13 (mod 20), after line f
						boolean here = (file == f % 20 || file == (f + 7) % 20 || file == (f + 13) % 20) && line == f;
						if (here && selected.contains(feature(f)))
							for (int k = 0; k < 5; k++)
								text.append("  ").append(feature(f)).append(" in ").append(file).append(" line ").append(k).append('\n');
					}
					for (int pair = 0; pair < 30; pair++) {
						int a = 1 + pair, b = 1 + (pair * 7 + 3) % FEATURES;
						if (a != b && file == pair % 20 && line == 40 + pair % 10 && selected.contains(feature(a)) && selected.contains(feature(b)))
							text.append("  ").append(feature(a)).append(" with ").append(feature(b)).append('\n');
					}
				}
				files.add(new FileContent("src/shared" + file + ".txt", text.toString()));
			}
			for (String feature : selected)
				if (!feature.equals("BASE")) {
					StringBuilder text = new StringBuilder();
					for (int line = 0; line < 20; line++)
						text.append(feature).append(" own line ").append(line).append('\n');
					files.add(new FileContent("features/" + feature + ".txt", text.toString()));
				}
			return files;
		}
	}

	/** 20 folders of 10 files of 108 lines (21,600 lines); [edit] 1 changes a line in every tenth file. */
	static final class LargeRepository {

		static List<FileContent> files(int edit) {
			List<FileContent> files = new ArrayList<>();
			for (int dir = 0; dir < 20; dir++)
				for (int file = 0; file < 10; file++) {
					StringBuilder text = new StringBuilder();
					for (int line = 0; line < 108; line++) {
						text.append("dir ").append(dir).append(" file ").append(file).append(" line ").append(line);
						if (edit == 1 && file == 0 && line == 54)
							text.append(" edited");
						text.append('\n');
					}
					files.add(new FileContent("dir" + dir + "/file" + file + ".txt", text.toString()));
				}
			return files;
		}
	}

	record FileContent(String path, String text) {
	}

	// --- steps ---

	@FunctionalInterface
	interface Measured {
		double seconds(Path copy) throws Exception;
	}

	/** {@value #RUNS} copies of the working directory [work], its repository included. */
	private static List<Path> copies(Path work, Path tmp, String name) throws IOException {
		List<Path> copies = new ArrayList<>();
		for (int i = 0; i < RUNS; i++) {
			Path copy = tmp.resolve(name + "-" + i);
			copyTree(work, copy);
			copies.add(copy);
		}
		return copies;
	}

	private static double median(List<Path> copies, Measured measured) throws Exception {
		double[] seconds = new double[copies.size()];
		for (int i = 0; i < copies.size(); i++)
			seconds[i] = measured.seconds(copies.get(i));
		Arrays.sort(seconds);
		return seconds[seconds.length / 2];
	}

	private static void check(String id, String what, double measured, double limit) {
		String line = String.format("%-8s %-40s %8.2f  (limit %s)", id, what, measured, limit);
		System.out.println(line);
		results.add(line);
		if (measured > limit)
			exceeded.add(line);
	}

	private static void assertWithinLimits() {
		if (!exceeded.isEmpty())
			fail("Over the limit:\n" + String.join("\n", exceeded));
	}

	private static void commit(Path work, List<FileContent> files, String configuration) throws Exception {
		writeVariant(work, files);
		ok(work, "commit", "-c", configuration, "-m", configuration);
	}

	/** The seconds the command took, from the start of its JVM to its exit; it must succeed. */
	private static double timed(Path dir, String... args) throws Exception {
		long start = System.nanoTime();
		ok(dir, args);
		return (System.nanoTime() - start) / 1e9;
	}

	private static String ok(Path dir, String... args) throws Exception {
		Process process = new ProcessBuilder(TestJvm.java(JVM_OPTIONS, "at.jku.isse.ecco.cli.Main", args))
				.directory(dir.toFile())
				.redirectOutput(ProcessBuilder.Redirect.PIPE)
				.redirectError(ProcessBuilder.Redirect.PIPE)
				.start();
		process.getOutputStream().close();
		CompletableFuture<String> out = new CompletableFuture<>();
		CompletableFuture<String> err = new CompletableFuture<>();
		Thread.ofVirtual().start(() -> out.complete(TestJvm.read(process.getInputStream())));
		Thread.ofVirtual().start(() -> err.complete(TestJvm.read(process.getErrorStream())));
		if (!process.waitFor(10, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			fail("ecco " + String.join(" ", args) + " did not finish within 10 minutes");
		}
		assertEquals(0, process.exitValue(), "ecco " + String.join(" ", args) + "\n" + out.join() + err.join());
		return out.join();
	}

	private static void writeVariant(Path work, List<FileContent> files) throws IOException {
		clear(work);
		for (FileContent file : files) {
			Path path = work.resolve(file.path());
			Files.createDirectories(path.getParent());
			Files.writeString(path, file.text(), StandardCharsets.UTF_8);
		}
	}

	/** Everything but the repository. */
	private static void clear(Path work) throws IOException {
		try (Stream<Path> top = Files.list(work)) {
			for (Path path : top.filter(p -> !p.getFileName().toString().equals(".ecco")).toList())
				deleteTree(path);
		}
	}

	private static void deleteTree(Path path) throws IOException {
		try (Stream<Path> walk = Files.walk(path)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList())
				Files.delete(p);
		}
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			walk.forEach(path -> {
				try {
					Path target = to.resolve(from.relativize(path).toString());
					if (Files.isDirectory(path))
						Files.createDirectories(target);
					else
						Files.copy(path, target);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	/** The bytes of the files under [repository]/[folder]; the repository's own if there is no such folder. */
	private static long size(Path repository, String folder) throws IOException {
		Path dir = Files.isDirectory(repository.resolve(folder)) ? repository.resolve(folder) : repository;
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter(Files::isRegularFile).mapToLong(p -> {
				try {
					return Files.size(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}).sum();
		}
	}
}
