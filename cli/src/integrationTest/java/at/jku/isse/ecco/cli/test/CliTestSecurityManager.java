package at.jku.isse.ecco.cli.test;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;

public class CliTestSecurityManager extends SecurityManager {

	private int expectedCode = 0;

	public CliTestSecurityManager(int expectedCode) {
		this.expectedCode = expectedCode;
	}

	@Override
	public void checkExit(int status) {
		if (this.expectedCode == status)
			throw new SuccessException("ERROR CODE: " + expectedCode);
		else
			throw new FailureException("ERROR CODE: " + expectedCode);
	}


	@Override
	public void checkPermission(Permission perm) {
	}

	@Override
	public void checkPermission(Permission perm, Object context) {
	}

	@Override
	public void checkCreateClassLoader() {
	}

	@Override
	public void checkAccess(Thread t) {
	}

	@Override
	public void checkAccess(ThreadGroup g) {
	}

	@Override
	public void checkExec(String cmd) {
	}

	@Override
	public void checkLink(String lib) {
	}

	@Override
	public void checkRead(FileDescriptor fd) {
	}

	@Override
	public void checkRead(String file) {
	}

	@Override
	public void checkRead(String file, Object context) {
	}

	@Override
	public void checkWrite(FileDescriptor fd) {
	}

	@Override
	public void checkWrite(String file) {
	}

	@Override
	public void checkDelete(String file) {
	}

	@Override
	public void checkConnect(String host, int port) {
	}

	@Override
	public void checkConnect(String host, int port, Object context) {
	}

	@Override
	public void checkListen(int port) {
	}

	@Override
	public void checkAccept(String host, int port) {
	}

	@Override
	public void checkMulticast(InetAddress maddr) {
	}

	@Override
	public void checkPropertiesAccess() {
	}

	@Override
	public void checkPropertyAccess(String key) {
	}

	@Override
	public boolean checkTopLevelWindow(Object window) {
		return super.checkTopLevelWindow(window);
	}

	@Override
	public void checkPrintJobAccess() {
	}

	@Override
	public void checkSystemClipboardAccess() {
	}

	@Override
	public void checkAwtEventQueueAccess() {
	}

	@Override
	public void checkPackageAccess(String pkg) {
	}

	@Override
	public void checkPackageDefinition(String pkg) {
	}

	@Override
	public void checkSetFactory() {
	}

	@Override
	public void checkMemberAccess(Class<?> clazz, int which) {
	}

	@Override
	public void checkSecurityAccess(String target) {
	}

}
