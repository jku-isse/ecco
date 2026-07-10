package at.jku.isse.ecco.service.git;

import java.time.Instant;

/** Plain-data snapshot of one commit's metadata, read by {@link GitHistoryReader}. */
public final class GitCommitInfo {

	private final String id;
	private final String shortId;
	private final String message;
	private final String author;
	private final Instant date;

	public GitCommitInfo(String id, String message, String author, Instant date) {
		this.id = id;
		this.shortId = id.substring(0, Math.min(7, id.length()));
		this.message = message;
		this.author = author;
		this.date = date;
	}

	public String getId() {
		return this.id;
	}

	/** First 7 characters of {@link #getId()} - the conventional git "short hash" length. */
	public String getShortId() {
		return this.shortId;
	}

	public String getMessage() {
		return this.message;
	}

	public String getAuthor() {
		return this.author;
	}

	public Instant getDate() {
		return this.date;
	}

}
