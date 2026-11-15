package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.storage.RepositoryReads;
import javafx.application.Platform;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every GUI test fails if a view read the repository's structures on the FX thread (NFR-C3, gaps #62,
 * #82): a commit holds the service's lock for its whole run and changes them meanwhile, so a view
 * reads them in the background holding the lock (RepositoryAccess, LatestRefresh) and keeps plain
 * values or copies. The storage reports every read of them ({@link RepositoryReads}, on with
 * -Decco.checkRepositoryReads=true, which the GUI's tests set); a read on the FX thread is a view's
 * where the innermost GUI frame calling it is the GUI's own code - a test reading the repository for
 * its own assertions is not, nor is a test {@link TestReadsOnTheFxThread}. Registered for every
 * GUI test (META-INF/services, autodetection).
 */
public class RepositoryReadsOffTheFxThread implements BeforeAllCallback, AfterEachCallback {

	private static final Set<String> VIOLATIONS = ConcurrentHashMap.newKeySet();

	@Override
	public void beforeAll(ExtensionContext context) {
		RepositoryReads.setCheck(RepositoryReadsOffTheFxThread::check);
	}

	@Override
	public void afterEach(ExtensionContext context) {
		if (VIOLATIONS.isEmpty())
			return;
		List<String> found = new ArrayList<>(VIOLATIONS);
		VIOLATIONS.clear();
		if (context.getRequiredTestMethod().isAnnotationPresent(TestReadsOnTheFxThread.class)
				|| context.getRequiredTestClass().isAnnotationPresent(TestReadsOnTheFxThread.class))
			return;
		fail("Repository structures read on the FX thread (NFR-C3) by " + found);
	}

	private static void check() {
		if (!Platform.isFxApplicationThread())
			return;
		List<StackWalker.StackFrame> gui = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
				.filter(frame -> frame.getDeclaringClass().getName().startsWith("at.jku.isse.ecco.gui.")
						&& frame.getDeclaringClass() != RepositoryReadsOffTheFxThread.class)
				.limit(3).toList());
		if (gui.isEmpty() || isTestCode(gui.get(0).getDeclaringClass()))
			return;
		// the innermost GUI frames, so that a shared helper names the view calling it
		VIOLATIONS.add(gui.stream().takeWhile(frame -> !isTestCode(frame.getDeclaringClass()))
				.map(frame -> frame.getClassName().replace("at.jku.isse.ecco.gui.", "") + "." + frame.getMethodName() + ":" + frame.getLineNumber())
				.collect(java.util.stream.Collectors.joining(" < ")));
	}

	/** Whether [type] was loaded from the test classes rather than the GUI's own. */
	private static boolean isTestCode(Class<?> type) {
		CodeSource source = type.getProtectionDomain().getCodeSource();
		return source != null && source.getLocation() != null && source.getLocation().getPath().contains("/test/");
	}
}
