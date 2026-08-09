package at.jku.isse.ecco.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

public class ProgressOutputStream extends FilterOutputStream {

	// throttles how often listeners are notified - firing on every single byte written would be
	// excessive, so only notify once progress has advanced by at least this much (as a 0.0-1.0
	// fraction of maxBytes) since the last fired event.
	private static final double PROGRESS_EVENT_THRESHOLD = 0.01;

	protected long bytesWritten;
	protected long maxBytes;

	protected ProgressOutputStream(OutputStream out) {
		this(out, -1);
	}

	protected ProgressOutputStream(OutputStream out, long maxBytes) {
		super(out);

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

	public long getBytesWritten() {
		return this.bytesWritten;
	}

	public double getProgress() {
		return ((double) this.bytesWritten) / ((double) this.maxBytes);
	}

	public void resetProgress() {
		this.bytesWritten = 0;
		this.lastProgress = 0.0;
	}

	protected double lastProgress;
	protected Collection<ProgressListener> progressListeners;

	public interface ProgressListener {
		public void writeProgressEvent(double progress, long bytes);
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
		if (progress - this.lastProgress >= PROGRESS_EVENT_THRESHOLD) {
			this.lastProgress = progress;
			for (ProgressListener progressListener : this.progressListeners) {
				progressListener.writeProgressEvent(progress, this.bytesWritten);
			}
		}
	}


	@Override
	public void write(byte[] b) throws IOException {
		this.bytesWritten += b.length;
		super.write(b);
		this.fireProgressEvent();
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		this.bytesWritten += len;
		super.write(b, off, len);
		this.fireProgressEvent();
	}

	@Override
	public void write(int b) throws IOException {
		this.bytesWritten++;
		super.write(b);
		this.fireProgressEvent();
	}

}
