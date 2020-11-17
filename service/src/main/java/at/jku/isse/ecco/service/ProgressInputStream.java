package at.jku.isse.ecco.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

public class ProgressInputStream extends FilterInputStream {

	protected long bytesRead;
	protected long maxBytes;

	protected ProgressInputStream(InputStream in) {
		this(in, -1);
	}

	protected ProgressInputStream(InputStream in, long maxBytes) {
		super(in);

		this.maxBytes = maxBytes;

		this.progressListeners = new ArrayList<>();

		this.resetProgress();
	}


	public long getMaxBytes() {
		return this.maxBytes;
	}

	public void setMaxBytes(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	public long getBytesRead() {
		return this.bytesRead;
	}

	public double getProgress() {
		return ((double) this.bytesRead) / ((double) this.maxBytes);
	}

	public void resetProgress() {
		this.bytesRead = 0;
		this.lastProgress = 0.0;
	}

	protected double lastProgress;
	protected Collection<ProgressListener> progressListeners;

	public interface ProgressListener {
		public void readProgressEvent(double progress, long bytes);
	}

	public void addListener(ProgressListener progressListener) {
		this.progressListeners.add(progressListener);
	}

	public void removeListener(ProgressListener progressListener) {
		this.progressListeners.remove(progressListener);
	}

	protected void fireProgressEvent() {
		if (this.maxBytes == -1)
			return;
		double progress = this.getProgress();
		if (this.lastProgress - progress >= 1.0) {
			this.lastProgress = progress;
			for (ProgressListener progressListener : this.progressListeners) {
				progressListener.readProgressEvent(progress, bytesRead);
			}
		}
	}


	@Override
	public int read() throws IOException {
		int val = super.read();
		if (val != -1) {
			this.bytesRead++;
			this.fireProgressEvent();
		}
		return val;
	}

	@Override
	public int read(byte[] b) throws IOException {
		int currentBytesRead = super.read(b);
		if (currentBytesRead != -1) {
			this.bytesRead += currentBytesRead;
			this.fireProgressEvent();
		}
		return currentBytesRead;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int currentBytesRead = super.read(b, off, len);
		if (currentBytesRead != -1) {
			this.bytesRead += currentBytesRead;
			this.fireProgressEvent();
		}
		return currentBytesRead;
	}

	@Override
	public long skip(long n) throws IOException {
		long bytesSkipped = super.skip(n);
		this.bytesRead += bytesSkipped;
		this.fireProgressEvent();
		return bytesSkipped;
	}

}
