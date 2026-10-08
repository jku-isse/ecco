package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text to write for each token of a file, in order: its stored text, except that inside
 * {@code \relative} a note's octave marks are those the note written before it calls for, and a
 * duration left implicit is written out when the note before it now has another.
 * <p>
 * With musical tokens, two spellings of one note are one token and ECCO keeps one of them - so a
 * note can be written after another note than the one it was spelled against: a variant with a
 * leap ({@code gis8 cis8 fis,8}) checked out as {@code gis8 cis8 fis8}, the {@code fis} an octave
 * high, because the variant without the leap had spelled it {@code fis8}. Each note's pitch is in
 * its meaning as diatonic steps from c (after {@code @}, see lymodel's lybar.normalize), so it can
 * be spelled again from the note actually written before it. Tokens without a meaning keep their
 * text, so without musical tokens nothing changes.
 */
final class LilypondRelativizer {

	private static final Pattern NOTE = Pattern.compile("([a-zA-Z]+(?:-[a-z]+)?)([',]*)([!?]?)(.*)", Pattern.DOTALL);
	private static final Pattern MEANING_PITCH = Pattern.compile("[a-zA-Z]+(?:-[a-z]+)?[',]*");

	private LilypondRelativizer() {}

	/** One relative block: the depth of the brace it opened at, and the pitch the next note is relative to. */
	private static final class Scope {
		final int depth;
		Integer reference;          // null: unknown - a variable was played, its notes are written elsewhere

		Scope(int depth, int reference) {
			this.depth = depth;
			this.reference = reference;
		}
	}

	static List<String> texts(List<DefaultTokenArtifactData> tokens) {
		List<String> out = new ArrayList<>(tokens.size());
		Deque<Scope> scopes = new ArrayDeque<>();
		Integer anchor = null;           // a \relative's pitch, waiting for its brace
		int depth = 0;
		boolean inChord = false;
		Integer chordFirst = null, chordPrevious = null;
		String lastDuration = null;
		boolean durationKnown = true;
		for (DefaultTokenArtifactData token : tokens) {
			String text = token.getText();
			String meaning = token.getMeaning();
			String action = token.getAction();
			if (action != null && action.startsWith("Name.Variable") && !action.equals("Name.Variable.Definition")) {
				// \someVariable: its notes, and so the pitch and duration the next note continues from,
				// are written elsewhere - the next note keeps its spelling and is the new reference
				if (!scopes.isEmpty()) scopes.peek().reference = null;
				durationKnown = false;
				out.add(text);
				continue;
			}
			if (meaning != null && meaning.startsWith("\\relative@")) {
				anchor = steps(meaning);
				out.add(text);
				continue;
			}
			if ("{".equals(text) || "<<".equals(text)) {
				depth++;
				if (anchor != null) {
					scopes.push(new Scope(depth, anchor));
					anchor = null;
				}
			} else if ("}".equals(text) || ">>".equals(text)) {
				if (!scopes.isEmpty() && scopes.peek().depth == depth) {
					scopes.pop();
				}
				depth--;
			} else if ("Delimiter.Chord.Start".equals(action)) {
				inChord = true;
				chordFirst = null;
				chordPrevious = scopes.isEmpty() ? null : scopes.peek().reference;
			}
			if (meaning == null) {
				out.add(text);
				continue;
			}
			Integer pitch = meaning.indexOf('@') >= 0 ? steps(meaning) : null;
			String written = text;
			if (pitch != null && !scopes.isEmpty()) {
				Scope scope = scopes.peek();
				Integer reference = inChord && chordPrevious != null ? chordPrevious : scope.reference;
				if (reference != null) written = respell(written, pitch, reference);
				if (inChord) {
					chordPrevious = pitch;
					if (chordFirst == null) chordFirst = pitch;
				} else {
					scope.reference = pitch;
				}
			}
			if (meaning.startsWith(">")) {          // a chord's end, with its duration
				inChord = false;
				if (chordFirst != null && !scopes.isEmpty()) scopes.peek().reference = chordFirst;
			}
			if (!inChord || meaning.startsWith(">")) {
				String duration = duration(meaning);
				if (!duration.isEmpty()) {
					// a duration left implicit holds if the note written before it has the duration
					// the note before it had in its own file - the same note, in a plain checkout
					String before = before(meaning);
					if (durationKnown && before != null && !hasDuration(written, meaning)
							&& !before.equals(lastDuration == null ? "" : lastDuration)) {
						written = written + duration;
					}
					lastDuration = duration;
					durationKnown = true;
				}
			}
			out.add(written);
		}
		return out;
	}

	private static int steps(String meaning) {
		int end = meaning.indexOf('~');
		return Integer.parseInt(meaning.substring(meaning.indexOf('@') + 1, end < 0 ? meaning.length() : end));
	}

	/** The duration of the note before, in the token's own file; null if the meaning does not say. */
	private static String before(String meaning) {
		int at = meaning.indexOf('~');
		return at < 0 ? null : meaning.substring(at + 1);
	}

	/** A meaning without what follows its @ or ~. */
	private static String value(String meaning) {
		int end = meaning.length();
		for (char mark : new char[] {'@', '~'}) {
			int at = meaning.indexOf(mark);
			if (at >= 0) end = Math.min(end, at);
		}
		return meaning.substring(0, end);
	}

	/** The written-out duration in a meaning: what follows its pitch (or rest, or chord end). */
	private static String duration(String meaning) {
		String value = value(meaning);
		if (value.startsWith(">")) return value.substring(1);
		if (value.matches("[rRs].*") && !value.matches("[rRs][a-z].*")) return value.substring(1);
		Matcher pitch = MEANING_PITCH.matcher(value);
		return pitch.lookingAt() ? value.substring(pitch.end()) : "";
	}

	/** Whether the token's own text writes a duration. */
	private static boolean hasDuration(String text, String meaning) {
		if (value(meaning).startsWith(">")) return text.length() > 1;
		if (text.matches("[rRs].*") && !text.matches("[rRs][a-z].*")) return text.length() > 1;
		Matcher note = NOTE.matcher(text);
		return note.matches() && !note.group(4).isEmpty();
	}

	/** [text] with the octave marks that make it [pitch] after a note at [reference], in relative mode. */
	static String respell(String text, int pitch, int reference) {
		Matcher note = NOTE.matcher(text);
		if (!note.matches()) return text;
		int nearest = reference + Math.floorMod(pitch - reference + 3, 7) - 3;
		int octaves = (pitch - nearest) / 7;
		String marks = octaves > 0 ? "'".repeat(octaves) : ",".repeat(-octaves);
		if (marks.equals(note.group(2))) return text;
		return note.group(1) + marks + note.group(3) + note.group(4);
	}
}
