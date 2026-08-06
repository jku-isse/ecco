package at.jku.isse.ecco.genericAdapter.grammarInferencer.diff;


/*
 * The Following implementation is based on the diff algorithm from:
 * Diff Match and Patch:
 *
 * Copyright 2006 Google Inc.
 * http://code.google.com/p/google-diff-match-patch/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Functions for diff, match and patch.
 * Computes the difference between two texts to create a patch.
 * Applies the patch onto another text, allowing for errors.
 *
 * @author fraser@google.com (Neil Fraser)
 *
 * adapted by Michael Jahn
 */

/**
 * Class containing the diff, match and patch methods.
 * Also contains the behaviour settings.
 */
public class diff_match_patch_list {

    // Defaults.
    // Set these on your diff_match_patch instance to override the defaults.

    /**
     * Number of seconds to map a diff before giving up (0 for infinity).
     */
    public float Diff_Timeout = 0f;
    /**
     * Cost of an empty edit operation in terms of edit characters.
     */
    public short Diff_EditCost = 4;
    /**
     * At what point is no match declared (0.0 = perfection, 1.0 = very loose).
     */
    public float Match_Threshold = 0f;
    /**
     * How far to search for a match (0 = exact location, 1000+ = broad match).
     * A match this many characters away from the expected location will add
     * 1.0 to the score (0.0 is a perfect match).
     */
    public int Match_Distance = 1000;
    /**
     * When deleting a large block of text (over ~64 characters), how close do
     * the contents have to be to match the expected contents. (0.0 = perfection,
     * 1.0 = very loose).  Note that Match_Threshold controls how closely the
     * end points of a delete need to match.
     */
    public float Patch_DeleteThreshold = 0.5f;
    /**
     * Chunk size for context length.
     */
    public short Patch_Margin = 4;

    /**
     * The number of bits in an int.
     */
    private short Match_MaxBits = 32;

    /**
     * Internal class for returning results from diff_linesToChars().
     * Other less paranoid languages just use a three-element array.
     */
    protected static class LinesToCharsResult {
        protected String chars1;
        protected String chars2;
        protected List<String> lineArray;

        protected LinesToCharsResult(String chars1, String chars2,
                                     List<String> lineArray) {
            this.chars1 = chars1;
            this.chars2 = chars2;
            this.lineArray = lineArray;
        }
    }


    //  DIFF FUNCTIONS


    /**
     * The data structure representing a diff is a Linked list of Diff objects:
     * {Diff(Operation.DELETE, "Hello"), Diff(Operation.INSERT, "Goodbye"),
     * Diff(Operation.EQUAL, " world.")}
     * which means: delete "Hello", add "Goodbye" and keep " world."
     */
    public enum Operation {
        DELETE, INSERT, EQUAL
    }

    /**
     * Find the differences between two texts.
     * Run a faster, slightly less optimal diff.
     * This method allows the 'checklines' of diff_main() to be optional.
     * Most of the time checklines is wanted, so default to true.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return Linked List of Diff objects.
     */
    public LinkedList<Diff> diff_main(List<String> text1, List<String> text2) {
        return diff_main(text1, text2, true);
    }

    /**
     * Find the differences between two texts.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @return Linked List of Diff objects.
     */
    public LinkedList<Diff> diff_main(List<String> text1, List<String> text2,
                                      boolean checklines) {
        // Set a deadline by which time the diff must be complete.
        long deadline;
        if (Diff_Timeout <= 0) {
            deadline = Long.MAX_VALUE;
        } else {
            deadline = System.currentTimeMillis() + (long) (Diff_Timeout * 1000);
        }
        return diff_main(text1, text2, checklines, deadline);
    }

    /**
     * Find the differences between two texts.  Simplifies the problem by
     * stripping any common prefix or suffix off the texts before diffing.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by.  Used
     *                   internally for recursive calls.  Users should set DiffTimeout instead.
     * @return Linked List of Diff objects.
     */
    private LinkedList<Diff> diff_main(List<String> text1, List<String> text2,
                                       boolean checklines, long deadline) {
        // Check for null inputs.
        if (text1 == null || text2 == null) {
            throw new IllegalArgumentException("Null inputs. (diff_main)");
        }

        // Check for equality (speedup).
        LinkedList<Diff> diffs;
        if (areEqual(text1, text2)) {
            diffs = new LinkedList<Diff>();
            if (text1.size() != 0) {
                diffs.add(new Diff(Operation.EQUAL, text1));
            }
            return diffs;
        }

        // Trim off common prefix (speedup).
        int commonlength = diff_commonPrefix(text1, text2);
        List<String> commonprefix = text1.subList(0, commonlength);
        text1 = text1.subList(commonlength, text1.size());
        text2 = text2.subList(commonlength, text2.size());

        // Trim off common suffix (speedup).

        commonlength = diff_commonSuffix(text1, text2);
        List<String> commonsuffix = text1.subList(text1.size() - commonlength, text1.size());
        text1 = text1.subList(0, text1.size() - commonlength);
        text2 = text2.subList(0, text2.size() - commonlength);


        // Compute the diff on the middle block.
        diffs = diff_compute(text1, text2, checklines, deadline);

        // Restore the prefix and suffix.
        if (commonprefix.size() != 0) {
            diffs.addFirst(new Diff(Operation.EQUAL, commonprefix));
        }
        if (commonsuffix.size() != 0) {
            diffs.addLast(new Diff(Operation.EQUAL, commonsuffix));
        }


//    diff_cleanupMerge(diffs);
        return diffs;
    }

    /**
     * Find the differences between two texts.  Assumes that the texts do not
     * have any common prefix or suffix.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private LinkedList<Diff> diff_compute(List<String> text1, List<String> text2,
                                          boolean checklines, long deadline) {
        LinkedList<Diff> diffs = new LinkedList<Diff>();

        if (text1.size() == 0) {
            // Just add some text (speedup).
            diffs.add(new Diff(Operation.INSERT, text2));
            return diffs;
        }

        if (text2.size() == 0) {
            // Just delete some text (speedup).
            diffs.add(new Diff(Operation.DELETE, text1));
            return diffs;
        }

        List<String> longtext = text1.size() > text2.size() ? text1 : text2;
        List<String> shorttext = text1.size() > text2.size() ? text2 : text1;

            int i = longtext.indexOf(shorttext);
            if (i != -1) {
                // Shorter text is inside the longer text (speedup).
                Operation op = (text1.size() > text2.size()) ?
                        Operation.DELETE : Operation.INSERT;
                diffs.add(new Diff(op, longtext.subList(0, i)));
                diffs.add(new Diff(Operation.EQUAL, shorttext));
                diffs.add(new Diff(op, longtext.subList(i + shorttext.size(), longtext.size())));
                return diffs;
            }



        if (shorttext.size() == 1) {
            // Single character string.
            // After the previous speedup, the character can't be an equality.
            diffs.add(new Diff(Operation.DELETE, text1));
            diffs.add(new Diff(Operation.INSERT, text2));
            return diffs;
        }

        return diff_bisect(text1, text2, deadline);
    }

    /**
     * Find the 'middle snake' of a diff, split the problem in two
     * and return the recursively constructed diff.
     * See Myers 1986 paper: An O(ND) Difference Algorithm and Its Variations.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    protected LinkedList<Diff> diff_bisect(List<String> text1, List<String> text2,
                                           long deadline) {
        // Cache the text lengths to prevent multiple calls.
        int text1_length = text1.size();
        int text2_length = text2.size();
        int max_d = (text1_length + text2_length + 1) / 2;
        int v_offset = max_d;
        int v_length = 2 * max_d;
        int[] v1 = new int[v_length];
        int[] v2 = new int[v_length];
        for (int x = 0; x < v_length; x++) {
            v1[x] = -1;
            v2[x] = -1;
        }
        v1[v_offset + 1] = 0;
        v2[v_offset + 1] = 0;
        int delta = text1_length - text2_length;
        // If the total number of characters is odd, then the front path will
        // collide with the reverse path.
        boolean front = (delta % 2 != 0);
        // Offsets for start and end of k loop.
        // Prevents mapping of space beyond the grid.
        int k1start = 0;
        int k1end = 0;
        int k2start = 0;
        int k2end = 0;
        for (int d = 0; d < max_d; d++) {
            // Bail out if deadline is reached.
            if (System.currentTimeMillis() > deadline) {
                break;
            }

            // Walk the front path one step.
            for (int k1 = -d + k1start; k1 <= d - k1end; k1 += 2) {
                int k1_offset = v_offset + k1;
                int x1;
                if (k1 == -d || (k1 != d && v1[k1_offset - 1] < v1[k1_offset + 1])) {
                    x1 = v1[k1_offset + 1];
                } else {
                    x1 = v1[k1_offset - 1] + 1;
                }
                int y1 = x1 - k1;
                while (x1 < text1_length && y1 < text2_length
                        && text1.get(x1).equals(text2.get(y1))) {
                    x1++;
                    y1++;
                }
                v1[k1_offset] = x1;
                if (x1 > text1_length) {
                    // Ran off the right of the graph.
                    k1end += 2;
                } else if (y1 > text2_length) {
                    // Ran off the bottom of the graph.
                    k1start += 2;
                } else if (front) {
                    int k2_offset = v_offset + delta - k1;
                    if (k2_offset >= 0 && k2_offset < v_length && v2[k2_offset] != -1) {
                        // Mirror x2 onto top-left coordinate system.
                        int x2 = text1_length - v2[k2_offset];
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diff_bisectSplit(text1, text2, x1, y1, deadline);
                        }
                    }
                }
            }

            // Walk the reverse path one step.
            for (int k2 = -d + k2start; k2 <= d - k2end; k2 += 2) {
                int k2_offset = v_offset + k2;
                int x2;
                if (k2 == -d || (k2 != d && v2[k2_offset - 1] < v2[k2_offset + 1])) {
                    x2 = v2[k2_offset + 1];
                } else {
                    x2 = v2[k2_offset - 1] + 1;
                }
                int y2 = x2 - k2;
                while (x2 < text1_length && y2 < text2_length
                        && text1.get(text1_length - x2 - 1).equals(text2.get(text2_length - y2 - 1))) {
                    x2++;
                    y2++;
                }
                v2[k2_offset] = x2;
                if (x2 > text1_length) {
                    // Ran off the left of the graph.
                    k2end += 2;
                } else if (y2 > text2_length) {
                    // Ran off the top of the graph.
                    k2start += 2;
                } else if (!front) {
                    int k1_offset = v_offset + delta - k2;
                    if (k1_offset >= 0 && k1_offset < v_length && v1[k1_offset] != -1) {
                        int x1 = v1[k1_offset];
                        int y1 = v_offset + x1 - k1_offset;
                        // Mirror x2 onto top-left coordinate system.
                        x2 = text1_length - x2;
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diff_bisectSplit(text1, text2, x1, y1, deadline);
                        }
                    }
                }
            }
        }
        // Diff took too long and hit the deadline or
        // number of diffs equals number of characters, no commonality at all.
        LinkedList<Diff> diffs = new LinkedList<Diff>();
        diffs.add(new Diff(Operation.DELETE, text1));
        diffs.add(new Diff(Operation.INSERT, text2));
        return diffs;
    }

    /**
     * Given the location of the 'middle snake', split the diff in two parts
     * and recurse.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param x        Index of split point in text1.
     * @param y        Index of split point in text2.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private LinkedList<Diff> diff_bisectSplit(List<String> text1, List<String> text2,
                                              int x, int y, long deadline) {
        List<String> text1a = text1.subList(0, x);
        List<String> text2a = text2.subList(0, y);
        List<String> text1b = text1.subList(x, text1.size());
        List<String> text2b = text2.subList(y, text2.size());

        // Compute both diffs serially.
        LinkedList<Diff> diffs = diff_main(text1a, text2a, false, deadline);
        LinkedList<Diff> diffsb = diff_main(text1b, text2b, false, deadline);

        diffs.addAll(diffsb);
        return diffs;
    }



    /**
     * Rehydrate the text in a diff from a string of line hashes to real lines of
     * text.
     *
     * @param diffs     LinkedList of Diff objects.
     * @param lineArray List of unique strings.
     */
    protected void diff_charsToLines(LinkedList<Diff> diffs,
                                     List<String> lineArray) {
        StringBuilder text;
        for (Diff diff : diffs) {
            text = new StringBuilder();
            for (int y = 0; y < diff.text.length(); y++) {
                text.append(lineArray.get(diff.text.charAt(y)));
            }
            diff.text = text.toString();
        }
    }

    /**
     * Determine the common prefix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the start of each string.
     */
    public int diff_commonPrefix(List<String> text1, List<String> text2) {
        // Performance analysis: http://neil.fraser.name/news/2007/10/09/#
        int minSize = Math.min(text1.size(), text2.size());
        for (int i = 0; i < minSize; i++) {
            if (!text1.get(i).equals(text2.get(i))) {
                return i;
            }
        }
        return minSize;
    }

    /**
     * Determine the common suffix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of each string.
     */
    public int diff_commonSuffix(List<String> text1, List<String> text2) {
        // Performance analysis: http://neil.fraser.name/news/2007/10/09/
        int text1_length = text1.size();
        int text2_length = text2.size();
        int n = Math.min(text1_length, text2_length);
        for (int i = 1; i <= n; i++) {
            if (!text1.get(text1_length - i).equals(text2.get(text2_length - i))) {
                return i - 1;
            }
        }
        return n;
    }

    /**
     * Determine if the suffix of one string is the prefix of another.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of the first
     * string and the start of the second string.
     */
    protected int diff_commonOverlap(String text1, String text2) {
        // Cache the text lengths to prevent multiple calls.
        int text1_length = text1.length();
        int text2_length = text2.length();
        // Eliminate the null case.
        if (text1_length == 0 || text2_length == 0) {
            return 0;
        }
        // Truncate the longer string.
        if (text1_length > text2_length) {
            text1 = text1.substring(text1_length - text2_length);
        } else if (text1_length < text2_length) {
            text2 = text2.substring(0, text1_length);
        }
        int text_length = Math.min(text1_length, text2_length);
        // Quick check for the worst case.
        if (text1.equals(text2)) {
            return text_length;
        }

        // Start by looking for a single character match
        // and increase length until no match is found.
        // Performance analysis: http://neil.fraser.name/news/2010/11/04/
        int best = 0;
        int length = 1;
        while (true) {
            String pattern = text1.substring(text_length - length);
            int found = text2.indexOf(pattern);
            if (found == -1) {
                return best;
            }
            length += found;
            if (found == 0 || text1.substring(text_length - length).equals(
                    text2.substring(0, length))) {
                best = length;
                length++;
            }
        }
    }

    /**
     * Do the two texts share a substring which is at least half the length of
     * the longer text?
     * This speedup can produce non-minimal diffs.
     * @param text1 First string.
     * @param text2 Second string.
     * @return Five element String array, containing the prefix of text1, the
     *     suffix of text1, the prefix of text2, the suffix of text2 and the
     *     common middle.  Or null if there was no match.
     */

    /**
     * Does a substring of shorttext exist within longtext such that the
     * substring is at least half the length of longtext?
     * @param longtext Longer string.
     * @param shorttext Shorter string.
     * @param i Start index of quarter length substring within longtext.
     * @return Five element String array, containing the prefix of longtext, the
     *     suffix of longtext, the prefix of shorttext, the suffix of shorttext
     *     and the common middle.  Or null if there was no match.
     */

    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     * @param diffs LinkedList of Diff objects.
     */

    /**
     * Look for single edits surrounded on both sides by equalities
     * which can be shifted sideways to align the edit to a word boundary.
     * e.g: The c<ins>at c</ins>ame. -> The <ins>cat </ins>came.
     * @param diffs LinkedList of Diff objects.
     */

    /**
     * Given two strings, compute a score representing whether the internal
     * boundary falls on logical boundaries.
     * Scores range from 6 (best) to 0 (worst).
     *
     * @param one First string.
     * @param two Second string.
     * @return The score.
     */


    /**
     * Reduce the number of edits by eliminating operationally trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    public void diff_cleanupEfficiency(LinkedList<Diff> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        boolean changes = false;
        Stack<Diff> equalities = new Stack<Diff>();  // Stack of equalities.
        String lastequality = null; // Always equal to equalities.lastElement().text
        ListIterator<Diff> pointer = diffs.listIterator();
        // Is there an insertion operation before the last equality.
        boolean pre_ins = false;
        // Is there a deletion operation before the last equality.
        boolean pre_del = false;
        // Is there an insertion operation after the last equality.
        boolean post_ins = false;
        // Is there a deletion operation after the last equality.
        boolean post_del = false;
        Diff thisDiff = pointer.next();
        Diff safeDiff = thisDiff;  // The last Diff that is known to be unsplitable.
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // Equality found.
                if (thisDiff.text.length() < Diff_EditCost && (post_ins || post_del)) {
                    // Candidate found.
                    equalities.push(thisDiff);
                    pre_ins = post_ins;
                    pre_del = post_del;
                    lastequality = thisDiff.text;
                } else {
                    // Not a candidate, and can never become one.
                    equalities.clear();
                    lastequality = null;
                    safeDiff = thisDiff;
                }
                post_ins = post_del = false;
            } else {
                // An insertion or deletion.
                if (thisDiff.operation == Operation.DELETE) {
                    post_del = true;
                } else {
                    post_ins = true;
                }
        /*
         * Five types to be split:
         * <ins>A</ins><del>B</del>XY<ins>C</ins><del>D</del>
         * <ins>A</ins>X<ins>C</ins><del>D</del>
         * <ins>A</ins><del>B</del>X<ins>C</ins>
         * <ins>A</del>X<ins>C</ins><del>D</del>
         * <ins>A</ins><del>B</del>X<del>C</del>
         */
                if (lastequality != null
                        && ((pre_ins && pre_del && post_ins && post_del)
                        || ((lastequality.length() < Diff_EditCost / 2)
                        && ((pre_ins ? 1 : 0) + (pre_del ? 1 : 0)
                        + (post_ins ? 1 : 0) + (post_del ? 1 : 0)) == 3))) {
                    //System.out.println("Splitting: '" + lastequality + "'");
                    // Walk back to offending equality.
                    while (thisDiff != equalities.lastElement()) {
                        thisDiff = pointer.previous();
                    }
                    pointer.next();

                    // Replace equality with a delete.
                    pointer.set(new Diff(Operation.DELETE, lastequality));
                    // Insert a corresponding an insert.
                    pointer.add(thisDiff = new Diff(Operation.INSERT, lastequality));

                    equalities.pop();  // Throw away the equality we just deleted.
                    lastequality = null;
                    if (pre_ins && pre_del) {
                        // No changes made which could affect previous entry, keep going.
                        post_ins = post_del = true;
                        equalities.clear();
                        safeDiff = thisDiff;
                    } else {
                        if (!equalities.empty()) {
                            // Throw away the previous equality (it needs to be reevaluated).
                            equalities.pop();
                        }
                        if (equalities.empty()) {
                            // There are no previous questionable equalities,
                            // walk back to the last known safe diff.
                            thisDiff = safeDiff;
                        } else {
                            // There is an equality we can fall back to.
                            thisDiff = equalities.lastElement();
                        }
                        while (thisDiff != pointer.previous()) {
                            // Intentionally empty loop.
                        }
                        post_ins = post_del = false;
                    }

                    changes = true;
                }
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }

        if (changes) {
            diff_cleanupMerge(diffs);
        }
    }

    /**
     * Reorder and merge like edit sections.  Merge equalities.
     * Any edit section can move as long as it doesn't cross an equality.
     *
     * @param diffs LinkedList of Diff objects.
     */
    public void diff_cleanupMerge(LinkedList<Diff> diffs) {
        diffs.add(new Diff(Operation.EQUAL, ""));  // Add a dummy entry at the end.
        ListIterator<Diff> pointer = diffs.listIterator();
        int count_delete = 0;
        int count_insert = 0;
        List<String> text_delete = new ArrayList<>();
        List<String> text_insert = new ArrayList<>();
        Diff thisDiff = pointer.next();
        Diff prevEqual = null;
        int commonlength;
        while (thisDiff != null) {
            switch (thisDiff.operation) {
                case INSERT:
                    count_insert++;
                    text_insert.add(thisDiff.text);
                    prevEqual = null;
                    break;
                case DELETE:
                    count_delete++;
                    text_delete.add(thisDiff.text);
                    prevEqual = null;
                    break;
                case EQUAL:
                    if (count_delete + count_insert > 1) {
                        boolean both_types = count_delete != 0 && count_insert != 0;
                        // Delete the offending records.
                        pointer.previous();  // Reverse direction.
                        while (count_delete-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        while (count_insert-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        if (both_types) {
                            // Factor out any common prefixies.
                            commonlength = diff_commonPrefix(text_insert, text_delete);
                            if (commonlength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous();
                                    assert thisDiff.operation == Operation.EQUAL
                                            : "Previous diff should have been an equality.";
                                    thisDiff.text += text_insert.subList(0, commonlength);
                                    pointer.next();
                                } else {
                                    pointer.add(new Diff(Operation.EQUAL,
                                            text_insert.subList(0, commonlength)));
                                }
                                text_insert = text_insert.subList(commonlength, text_insert.size());
                                text_delete = text_delete.subList(commonlength, text_delete.size());
                            }
                            // Factor out any common suffixies.
                            commonlength = diff_commonSuffix(text_insert, text_delete);
                            if (commonlength != 0) {
                                thisDiff = pointer.next();
                                thisDiff.text = text_insert.subList(text_insert.size() - commonlength, text_insert.size()) + thisDiff.text;
                                text_insert = text_insert.subList(0, text_insert.size() - commonlength);
                                text_delete = text_delete.subList(0, text_delete.size() - commonlength);
                                pointer.previous();
                            }
                        }
                        // Insert the merged records.
                        if (text_delete.size() != 0) {
                            pointer.add(new Diff(Operation.DELETE, text_delete));
                        }
                        if (text_insert.size() != 0) {
                            pointer.add(new Diff(Operation.INSERT, text_insert));
                        }
                        // Step forward to the equality.
                        thisDiff = pointer.hasNext() ? pointer.next() : null;
                    } else if (prevEqual != null) {
                        // Merge this equality with the previous one.
                        prevEqual.text += thisDiff.text;
                        pointer.remove();
                        thisDiff = pointer.previous();
                        pointer.next();  // Forward direction
                    }
                    count_insert = 0;
                    count_delete = 0;
                    text_delete = new ArrayList<>();
                    text_insert = new ArrayList<>();
                    prevEqual = thisDiff;
                    break;
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }
        if (diffs.getLast().text.length() == 0) {
            diffs.removeLast();  // Remove the dummy entry at the end.
        }

    /*
     * Second pass: look for single edits surrounded on both sides by equalities
     * which can be shifted sideways to eliminate an equality.
     * e.g: A<ins>BA</ins>C -> <ins>AB</ins>AC
     */
        boolean changes = false;
        // Create a new iterator at the start.
        // (As opposed to walking the current one back.)
        pointer = diffs.listIterator();
        Diff prevDiff = pointer.hasNext() ? pointer.next() : null;
        thisDiff = pointer.hasNext() ? pointer.next() : null;
        Diff nextDiff = pointer.hasNext() ? pointer.next() : null;
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff.operation == Operation.EQUAL &&
                    nextDiff.operation == Operation.EQUAL) {
                // This is a single edit surrounded by equalities.
                if (thisDiff.text.endsWith(prevDiff.text)) {
                    // Shift the edit over the previous equality.
                    thisDiff.text = prevDiff.text
                            + thisDiff.text.substring(0, thisDiff.text.length()
                            - prevDiff.text.length());
                    nextDiff.text = prevDiff.text + nextDiff.text;
                    pointer.previous(); // Walk past nextDiff.
                    pointer.previous(); // Walk past thisDiff.
                    pointer.previous(); // Walk past prevDiff.
                    pointer.remove(); // Delete prevDiff.
                    pointer.next(); // Walk past thisDiff.
                    thisDiff = pointer.next(); // Walk past nextDiff.
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                } else if (thisDiff.text.startsWith(nextDiff.text)) {
                    // Shift the edit over the next equality.
                    prevDiff.text += nextDiff.text;
                    thisDiff.text = thisDiff.text.substring(nextDiff.text.length())
                            + nextDiff.text;
                    pointer.remove(); // Delete nextDiff.
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                }
            }
            prevDiff = thisDiff;
            thisDiff = nextDiff;
            nextDiff = pointer.hasNext() ? pointer.next() : null;
        }
        // If shifts were made, the diff needs reordering and another shift sweep.
        if (changes) {
            diff_cleanupMerge(diffs);
        }
    }

    /**
     * loc is a location in text1, compute and return the equivalent location in
     * text2.
     * e.g. "The cat" vs "The big cat", 1->1, 5->8
     *
     * @param diffs LinkedList of Diff objects.
     * @param loc   Location within text1.
     * @return Location within text2.
     */
    public int diff_xIndex(LinkedList<Diff> diffs, int loc) {
        int chars1 = 0;
        int chars2 = 0;
        int last_chars1 = 0;
        int last_chars2 = 0;
        Diff lastDiff = null;
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.INSERT) {
                // Equality or deletion.
                chars1 += aDiff.text.length();
            }
            if (aDiff.operation != Operation.DELETE) {
                // Equality or insertion.
                chars2 += aDiff.text.length();
            }
            if (chars1 > loc) {
                // Overshot the location.
                lastDiff = aDiff;
                break;
            }
            last_chars1 = chars1;
            last_chars2 = chars2;
        }
        if (lastDiff != null && lastDiff.operation == Operation.DELETE) {
            // The location was deleted.
            return last_chars2;
        }
        // Add the remaining character length.
        return last_chars2 + (loc - last_chars1);
    }

    /**
     * Convert a Diff list into a pretty HTML report.
     *
     * @param diffs LinkedList of Diff objects.
     * @return HTML representation.
     */
    public String diff_prettyHtml(LinkedList<Diff> diffs) {
        StringBuilder html = new StringBuilder();
        for (Diff aDiff : diffs) {
            String text = aDiff.text.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\n", "&para;<br>");
            switch (aDiff.operation) {
                case INSERT:
                    html.append("<ins style=\"background:#e6ffe6;\">").append(text)
                            .append("</ins>");
                    break;
                case DELETE:
                    html.append("<del style=\"background:#ffe6e6;\">").append(text)
                            .append("</del>");
                    break;
                case EQUAL:
                    html.append("<span>").append(text).append("</span>");
                    break;
            }
        }
        return html.toString();
    }

    /**
     * Compute and return the source text (all equalities and deletions).
     *
     * @param diffs LinkedList of Diff objects.
     * @return Source text.
     */
    public String diff_text1(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.INSERT) {
                text.append(aDiff.text);
            }
        }
        return text.toString();
    }

    /**
     * Compute and return the destination text (all equalities and insertions).
     *
     * @param diffs LinkedList of Diff objects.
     * @return Destination text.
     */
    public String diff_text2(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.DELETE) {
                text.append(aDiff.text);
            }
        }
        return text.toString();
    }

    /**
     * Compute the Levenshtein distance; the number of inserted, deleted or
     * substituted characters.
     *
     * @param diffs LinkedList of Diff objects.
     * @return Number of changes.
     */
    public int diff_levenshtein(LinkedList<Diff> diffs) {
        int levenshtein = 0;
        int insertions = 0;
        int deletions = 0;
        for (Diff aDiff : diffs) {
            switch (aDiff.operation) {
                case INSERT:
                    insertions += aDiff.text.length();
                    break;
                case DELETE:
                    deletions += aDiff.text.length();
                    break;
                case EQUAL:
                    // A deletion and an insertion is one substitution.
                    levenshtein += Math.max(insertions, deletions);
                    insertions = 0;
                    deletions = 0;
                    break;
            }
        }
        levenshtein += Math.max(insertions, deletions);
        return levenshtein;
    }

    /**
     * Crush the diff into an encoded string which describes the operations
     * required to transform text1 into text2.
     * E.g. =3\t-2\t+ing  -> Keep 3 chars, delete 2 chars, insert 'ing'.
     * Operations are tab-separated.  Inserted text is escaped using %xx notation.
     *
     * @param diffs Array of Diff objects.
     * @return Delta text.
     */
    public String diff_toDelta(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            switch (aDiff.operation) {
                case INSERT:
                    try {
                        text.append("+").append(URLEncoder.encode(aDiff.text, "UTF-8")
                                .replace('+', ' ')).append("\t");
                    } catch (UnsupportedEncodingException e) {
                        // Not likely on modern system.
                        throw new Error("This system does not support UTF-8.", e);
                    }
                    break;
                case DELETE:
                    text.append("-").append(aDiff.text.length()).append("\t");
                    break;
                case EQUAL:
                    text.append("=").append(aDiff.text.length()).append("\t");
                    break;
            }
        }
        String delta = text.toString();
        if (delta.length() != 0) {
            // Strip off trailing tab character.
            delta = delta.substring(0, delta.length() - 1);
            delta = unescapeForEncodeUriCompatability(delta);
        }
        return delta;
    }

    /**
     * Given the original text1, and an encoded string which describes the
     * operations required to transform text1 into text2, compute the full diff.
     *
     * @param text1 Source string for the diff.
     * @param delta Delta text.
     * @return Array of Diff objects or null if invalid.
     * @throws IllegalArgumentException If invalid input.
     */
    public LinkedList<Diff> diff_fromDelta(String text1, String delta)
            throws IllegalArgumentException {
        LinkedList<Diff> diffs = new LinkedList<Diff>();
        int pointer = 0;  // Cursor in text1
        String[] tokens = delta.split("\t");
        for (String token : tokens) {
            if (token.length() == 0) {
                // Blank tokens are ok (from a trailing \t).
                continue;
            }
            // Each token begins with a one character parameter which specifies the
            // operation of this token (delete, insert, equality).
            String param = token.substring(1);
            switch (token.charAt(0)) {
                case '+':
                    // decode would change all "+" to " "
                    param = param.replace("+", "%2B");
                    try {
                        param = URLDecoder.decode(param, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        // Not likely on modern system.
                        throw new Error("This system does not support UTF-8.", e);
                    } catch (IllegalArgumentException e) {
                        // Malformed URI sequence.
                        throw new IllegalArgumentException(
                                "Illegal escape in diff_fromDelta: " + param, e);
                    }
                    diffs.add(new Diff(Operation.INSERT, param));
                    break;
                case '-':
                    // Fall through.
                case '=':
                    int n;
                    try {
                        n = Integer.parseInt(param);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid number in diff_fromDelta: " + param, e);
                    }
                    if (n < 0) {
                        throw new IllegalArgumentException(
                                "Negative number in diff_fromDelta: " + param);
                    }
                    String text;
                    try {
                        text = text1.substring(pointer, pointer += n);
                    } catch (StringIndexOutOfBoundsException e) {
                        throw new IllegalArgumentException("Delta length (" + pointer
                                + ") larger than source text length (" + text1.length()
                                + ").", e);
                    }
                    if (token.charAt(0) == '=') {
                        diffs.add(new Diff(Operation.EQUAL, text));
                    } else {
                        diffs.add(new Diff(Operation.DELETE, text));
                    }
                    break;
                default:
                    // Anything else is an error.
                    throw new IllegalArgumentException(
                            "Invalid diff operation in diff_fromDelta: " + token.charAt(0));
            }
        }
        if (pointer != text1.length()) {
            throw new IllegalArgumentException("Delta length (" + pointer
                    + ") smaller than source text length (" + text1.length() + ").");
        }
        return diffs;
    }


    //  MATCH FUNCTIONS


    /**
     * Locate the best instance of 'pattern' in 'text' near 'loc'.
     * Returns -1 if no match found.
     *
     * @param text    The text to search.
     * @param pattern The pattern to search for.
     * @param loc     The location to search around.
     * @return Best match index or -1.
     */
    public int match_main(String text, String pattern, int loc) {
        // Check for null inputs.
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("Null inputs. (match_main)");
        }

        loc = Math.max(0, Math.min(loc, text.length()));
        if (text.equals(pattern)) {
            // Shortcut (potentially not guaranteed by the algorithm)
            return 0;
        } else if (text.length() == 0) {
            // Nothing to match.
            return -1;
        } else if (loc + pattern.length() <= text.length()
                && text.substring(loc, loc + pattern.length()).equals(pattern)) {
            // Perfect match at the perfect spot!  (Includes case of null pattern)
            return loc;
        } else {
            // Do a fuzzy compare.
            return match_bitap(text, pattern, loc);
        }
    }

    /**
     * Locate the best instance of 'pattern' in 'text' near 'loc' using the
     * Bitap algorithm.  Returns -1 if no match found.
     *
     * @param text    The text to search.
     * @param pattern The pattern to search for.
     * @param loc     The location to search around.
     * @return Best match index or -1.
     */
    protected int match_bitap(String text, String pattern, int loc) {
        assert (Match_MaxBits == 0 || pattern.length() <= Match_MaxBits)
                : "Pattern too long for this application.";

        // Initialise the alphabet.
        Map<Character, Integer> s = match_alphabet(pattern);

        // Highest score beyond which we give up.
        double score_threshold = Match_Threshold;
        // Is there a nearby exact match? (speedup)
        int best_loc = text.indexOf(pattern, loc);
        if (best_loc != -1) {
            score_threshold = Math.min(match_bitapScore(0, best_loc, loc, pattern),
                    score_threshold);
            // What about in the other direction? (speedup)
            best_loc = text.lastIndexOf(pattern, loc + pattern.length());
            if (best_loc != -1) {
                score_threshold = Math.min(match_bitapScore(0, best_loc, loc, pattern),
                        score_threshold);
            }
        }

        // Initialise the bit arrays.
        int matchmask = 1 << (pattern.length() - 1);
        best_loc = -1;

        int bin_min, bin_mid;
        int bin_max = pattern.length() + text.length();
        // Empty initialization added to appease Java compiler.
        int[] last_rd = new int[0];
        for (int d = 0; d < pattern.length(); d++) {
            // Scan for the best match; each iteration allows for one more error.
            // Run a binary search to determine how far from 'loc' we can stray at
            // this error level.
            bin_min = 0;
            bin_mid = bin_max;
            while (bin_min < bin_mid) {
                if (match_bitapScore(d, loc + bin_mid, loc, pattern)
                        <= score_threshold) {
                    bin_min = bin_mid;
                } else {
                    bin_max = bin_mid;
                }
                bin_mid = (bin_max - bin_min) / 2 + bin_min;
            }
            // Use the result from this iteration as the maximum for the next.
            bin_max = bin_mid;
            int start = Math.max(1, loc - bin_mid + 1);
            int finish = Math.min(loc + bin_mid, text.length()) + pattern.length();

            int[] rd = new int[finish + 2];
            rd[finish + 1] = (1 << d) - 1;
            for (int j = finish; j >= start; j--) {
                int charMatch;
                if (text.length() <= j - 1 || !s.containsKey(text.charAt(j - 1))) {
                    // Out of range.
                    charMatch = 0;
                } else {
                    charMatch = s.get(text.charAt(j - 1));
                }
                if (d == 0) {
                    // First pass: exact match.
                    rd[j] = ((rd[j + 1] << 1) | 1) & charMatch;
                } else {
                    // Subsequent passes: fuzzy match.
                    rd[j] = (((rd[j + 1] << 1) | 1) & charMatch)
                            | (((last_rd[j + 1] | last_rd[j]) << 1) | 1) | last_rd[j + 1];
                }
                if ((rd[j] & matchmask) != 0) {
                    double score = match_bitapScore(d, j - 1, loc, pattern);
                    // This match will almost certainly be better than any existing
                    // match.  But check anyway.
                    if (score <= score_threshold) {
                        // Told you so.
                        score_threshold = score;
                        best_loc = j - 1;
                        if (best_loc > loc) {
                            // When passing loc, don't exceed our current distance from loc.
                            start = Math.max(1, 2 * loc - best_loc);
                        } else {
                            // Already passed loc, downhill from here on in.
                            break;
                        }
                    }
                }
            }
            if (match_bitapScore(d + 1, loc, loc, pattern) > score_threshold) {
                // No hope for a (better) match at greater error levels.
                break;
            }
            last_rd = rd;
        }
        return best_loc;
    }

    /**
     * Compute and return the score for a match with e errors and x location.
     *
     * @param e       Number of errors in match.
     * @param x       Location of match.
     * @param loc     Expected location of match.
     * @param pattern Pattern being sought.
     * @return Overall score for match (0.0 = good, 1.0 = bad).
     */
    private double match_bitapScore(int e, int x, int loc, String pattern) {
        float accuracy = (float) e / pattern.length();
        int proximity = Math.abs(loc - x);
        if (Match_Distance == 0) {
            // Dodge divide by zero error.
            return proximity == 0 ? accuracy : 1.0;
        }
        return accuracy + (proximity / (float) Match_Distance);
    }

    /**
     * Initialise the alphabet for the Bitap algorithm.
     *
     * @param pattern The text to encode.
     * @return Hash of character locations.
     */
    protected Map<Character, Integer> match_alphabet(String pattern) {
        Map<Character, Integer> s = new HashMap<Character, Integer>();
        char[] char_pattern = pattern.toCharArray();
        for (char c : char_pattern) {
            s.put(c, 0);
        }
        int i = 0;
        for (char c : char_pattern) {
            s.put(c, s.get(c) | (1 << (pattern.length() - i - 1)));
            i++;
        }
        return s;
    }


    //  PATCH FUNCTIONS


    /**
     * Increase the context until it is unique,
     * but don't let the pattern expand beyond Match_MaxBits.
     *
     * @param patch The patch to grow.
     * @param text  Source text.
     */
    protected void patch_addContext(Patch patch, String text) {
        if (text.length() == 0) {
            return;
        }
        String pattern = text.substring(patch.start2, patch.start2 + patch.length1);
        int padding = 0;

        // Look for the first and last matches of pattern in text.  If two different
        // matches are found, increase the pattern length.
        while (text.indexOf(pattern) != text.lastIndexOf(pattern)
                && pattern.length() < Match_MaxBits - Patch_Margin - Patch_Margin) {
            padding += Patch_Margin;
            pattern = text.substring(Math.max(0, patch.start2 - padding),
                    Math.min(text.length(), patch.start2 + patch.length1 + padding));
        }
        // Add one chunk for good luck.
        padding += Patch_Margin;

        // Add the prefix.
        String prefix = text.substring(Math.max(0, patch.start2 - padding),
                patch.start2);
        if (prefix.length() != 0) {
            patch.diffs.addFirst(new Diff(Operation.EQUAL, prefix));
        }
        // Add the suffix.
        String suffix = text.substring(patch.start2 + patch.length1,
                Math.min(text.length(), patch.start2 + patch.length1 + padding));
        if (suffix.length() != 0) {
            patch.diffs.addLast(new Diff(Operation.EQUAL, suffix));
        }

        // Roll back the start points.
        patch.start1 -= prefix.length();
        patch.start2 -= prefix.length();
        // Extend the lengths.
        patch.length1 += prefix.length() + suffix.length();
        patch.length2 += prefix.length() + suffix.length();
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * A set of diffs will be computed.
     * @param text1 Old text.
     * @param text2 New text.
     * @return LinkedList of Patch objects.
     */
  /*
  public LinkedList<Patch> patch_make(String text1, String text2) {
    if (text1 == null || text2 == null) {
      throw new IllegalArgumentException("Null inputs. (patch_make)");
    }
    // No diffs provided, compute our own.
    LinkedList<Diff> diffs = diff_main(text1, text2, true);
    if (diffs.size() > 2) {
      diff_cleanupSemantic(diffs);
      diff_cleanupEfficiency(diffs);
    }
    return patch_make(text1, diffs);
  }
  */

    /**
     * Compute a list of patches to turn text1 into text2.
     * text1 will be derived from the provided diffs.
     *
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of Patch objects.
     */
    public LinkedList<Patch> patch_make(LinkedList<Diff> diffs) {
        if (diffs == null) {
            throw new IllegalArgumentException("Null inputs. (patch_make)");
        }
        // No origin string provided, compute our own.
        String text1 = diff_text1(diffs);
        return patch_make(text1, diffs);
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is ignored, diffs are the delta between text1 and text2.
     *
     * @param text1 Old text
     * @param text2 Ignored.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of Patch objects.
     * @deprecated Prefer patch_make(String text1, LinkedList<Diff> diffs).
     */
    public LinkedList<Patch> patch_make(String text1, String text2,
                                        LinkedList<Diff> diffs) {
        return patch_make(text1, diffs);
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is not provided, diffs are the delta between text1 and text2.
     *
     * @param text1 Old text.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of Patch objects.
     */
    public LinkedList<Patch> patch_make(String text1, LinkedList<Diff> diffs) {
        if (text1 == null || diffs == null) {
            throw new IllegalArgumentException("Null inputs. (patch_make)");
        }

        LinkedList<Patch> patches = new LinkedList<Patch>();
        if (diffs.isEmpty()) {
            return patches;  // Get rid of the null case.
        }
        Patch patch = new Patch();
        int char_count1 = 0;  // Number of characters into the text1 string.
        int char_count2 = 0;  // Number of characters into the text2 string.
        // Start with text1 (prepatch_text) and apply the diffs until we arrive at
        // text2 (postpatch_text). We recreate the patches one by one to determine
        // context info.
        String prepatch_text = text1;
        String postpatch_text = text1;
        for (Diff aDiff : diffs) {
            if (patch.diffs.isEmpty() && aDiff.operation != Operation.EQUAL) {
                // A new patch starts here.
                patch.start1 = char_count1;
                patch.start2 = char_count2;
            }

            switch (aDiff.operation) {
                case INSERT:
                    patch.diffs.add(aDiff);
                    patch.length2 += aDiff.text.length();
                    postpatch_text = postpatch_text.substring(0, char_count2)
                            + aDiff.text + postpatch_text.substring(char_count2);
                    break;
                case DELETE:
                    patch.length1 += aDiff.text.length();
                    patch.diffs.add(aDiff);
                    postpatch_text = postpatch_text.substring(0, char_count2)
                            + postpatch_text.substring(char_count2 + aDiff.text.length());
                    break;
                case EQUAL:
                    if (aDiff.text.length() <= 2 * Patch_Margin
                            && !patch.diffs.isEmpty() && aDiff != diffs.getLast()) {
                        // Small equality inside a patch.
                        patch.diffs.add(aDiff);
                        patch.length1 += aDiff.text.length();
                        patch.length2 += aDiff.text.length();
                    }

                    if (aDiff.text.length() >= 2 * Patch_Margin) {
                        // Time for a new patch.
                        if (!patch.diffs.isEmpty()) {
                            patch_addContext(patch, prepatch_text);
                            patches.add(patch);
                            patch = new Patch();
                            // Unlike Unidiff, our patch lists have a rolling context.
                            // http://code.google.com/p/google-diff-match-patch/wiki/Unidiff
                            // Update prepatch text & pos to reflect the application of the
                            // just completed patch.
                            prepatch_text = postpatch_text;
                            char_count1 = char_count2;
                        }
                    }
                    break;
            }

            // Update the current character count.
            if (aDiff.operation != Operation.INSERT) {
                char_count1 += aDiff.text.length();
            }
            if (aDiff.operation != Operation.DELETE) {
                char_count2 += aDiff.text.length();
            }
        }
        // Pick up the leftover patch if not empty.
        if (!patch.diffs.isEmpty()) {
            patch_addContext(patch, prepatch_text);
            patches.add(patch);
        }

        return patches;
    }

    /**
     * Given an array of patches, return another array that is identical.
     *
     * @param patches Array of Patch objects.
     * @return Array of Patch objects.
     */
    public LinkedList<Patch> patch_deepCopy(LinkedList<Patch> patches) {
        LinkedList<Patch> patchesCopy = new LinkedList<Patch>();
        for (Patch aPatch : patches) {
            Patch patchCopy = new Patch();
            for (Diff aDiff : aPatch.diffs) {
                Diff diffCopy = new Diff(aDiff.operation, aDiff.text);
                patchCopy.diffs.add(diffCopy);
            }
            patchCopy.start1 = aPatch.start1;
            patchCopy.start2 = aPatch.start2;
            patchCopy.length1 = aPatch.length1;
            patchCopy.length2 = aPatch.length2;
            patchesCopy.add(patchCopy);
        }
        return patchesCopy;
    }

    /**
     * Add some padding on text start and end so that edges can match something.
     * Intended to be called only from within patch_apply.
     *
     * @param patches Array of Patch objects.
     * @return The padding string added to each side.
     */
    public String patch_addPadding(LinkedList<Patch> patches) {
        short paddingLength = this.Patch_Margin;
        String nullPadding = "";
        for (short x = 1; x <= paddingLength; x++) {
            nullPadding += String.valueOf((char) x);
        }

        // Bump all the patches forward.
        for (Patch aPatch : patches) {
            aPatch.start1 += paddingLength;
            aPatch.start2 += paddingLength;
        }

        // Add some padding on start of first diff.
        Patch patch = patches.getFirst();
        LinkedList<Diff> diffs = patch.diffs;
        if (diffs.isEmpty() || diffs.getFirst().operation != Operation.EQUAL) {
            // Add nullPadding equality.
            diffs.addFirst(new Diff(Operation.EQUAL, nullPadding));
            patch.start1 -= paddingLength;  // Should be 0.
            patch.start2 -= paddingLength;  // Should be 0.
            patch.length1 += paddingLength;
            patch.length2 += paddingLength;
        } else if (paddingLength > diffs.getFirst().text.length()) {
            // Grow first equality.
            Diff firstDiff = diffs.getFirst();
            int extraLength = paddingLength - firstDiff.text.length();
            firstDiff.text = nullPadding.substring(firstDiff.text.length())
                    + firstDiff.text;
            patch.start1 -= extraLength;
            patch.start2 -= extraLength;
            patch.length1 += extraLength;
            patch.length2 += extraLength;
        }

        // Add some padding on end of last diff.
        patch = patches.getLast();
        diffs = patch.diffs;
        if (diffs.isEmpty() || diffs.getLast().operation != Operation.EQUAL) {
            // Add nullPadding equality.
            diffs.addLast(new Diff(Operation.EQUAL, nullPadding));
            patch.length1 += paddingLength;
            patch.length2 += paddingLength;
        } else if (paddingLength > diffs.getLast().text.length()) {
            // Grow last equality.
            Diff lastDiff = diffs.getLast();
            int extraLength = paddingLength - lastDiff.text.length();
            lastDiff.text += nullPadding.substring(0, extraLength);
            patch.length1 += extraLength;
            patch.length2 += extraLength;
        }

        return nullPadding;
    }

    /**
     * Look through the patches and break up any which are longer than the
     * maximum limit of the match algorithm.
     * Intended to be called only from within patch_apply.
     *
     * @param patches LinkedList of Patch objects.
     */
    public void patch_splitMax(LinkedList<Patch> patches) {
        short patch_size = Match_MaxBits;
        String precontext, postcontext;
        Patch patch;
        int start1, start2;
        boolean empty;
        Operation diff_type;
        String diff_text;
        ListIterator<Patch> pointer = patches.listIterator();
        Patch bigpatch = pointer.hasNext() ? pointer.next() : null;
        while (bigpatch != null) {
            if (bigpatch.length1 <= Match_MaxBits) {
                bigpatch = pointer.hasNext() ? pointer.next() : null;
                continue;
            }
            // Remove the big old patch.
            pointer.remove();
            start1 = bigpatch.start1;
            start2 = bigpatch.start2;
            precontext = "";
            while (!bigpatch.diffs.isEmpty()) {
                // Create one of several smaller patches.
                patch = new Patch();
                empty = true;
                patch.start1 = start1 - precontext.length();
                patch.start2 = start2 - precontext.length();
                if (precontext.length() != 0) {
                    patch.length1 = patch.length2 = precontext.length();
                    patch.diffs.add(new Diff(Operation.EQUAL, precontext));
                }
                while (!bigpatch.diffs.isEmpty()
                        && patch.length1 < patch_size - Patch_Margin) {
                    diff_type = bigpatch.diffs.getFirst().operation;
                    diff_text = bigpatch.diffs.getFirst().text;
                    if (diff_type == Operation.INSERT) {
                        // Insertions are harmless.
                        patch.length2 += diff_text.length();
                        start2 += diff_text.length();
                        patch.diffs.addLast(bigpatch.diffs.removeFirst());
                        empty = false;
                    } else if (diff_type == Operation.DELETE && patch.diffs.size() == 1
                            && patch.diffs.getFirst().operation == Operation.EQUAL
                            && diff_text.length() > 2 * patch_size) {
                        // This is a large deletion.  Let it pass in one chunk.
                        patch.length1 += diff_text.length();
                        start1 += diff_text.length();
                        empty = false;
                        patch.diffs.add(new Diff(diff_type, diff_text));
                        bigpatch.diffs.removeFirst();
                    } else {
                        // Deletion or equality.  Only take as much as we can stomach.
                        diff_text = diff_text.substring(0, Math.min(diff_text.length(),
                                patch_size - patch.length1 - Patch_Margin));
                        patch.length1 += diff_text.length();
                        start1 += diff_text.length();
                        if (diff_type == Operation.EQUAL) {
                            patch.length2 += diff_text.length();
                            start2 += diff_text.length();
                        } else {
                            empty = false;
                        }
                        patch.diffs.add(new Diff(diff_type, diff_text));
                        if (diff_text.equals(bigpatch.diffs.getFirst().text)) {
                            bigpatch.diffs.removeFirst();
                        } else {
                            bigpatch.diffs.getFirst().text = bigpatch.diffs.getFirst().text
                                    .substring(diff_text.length());
                        }
                    }
                }
                // Compute the head context for the next patch.
                precontext = diff_text2(patch.diffs);
                precontext = precontext.substring(Math.max(0, precontext.length()
                        - Patch_Margin));
                // Append the end context for this patch.
                if (diff_text1(bigpatch.diffs).length() > Patch_Margin) {
                    postcontext = diff_text1(bigpatch.diffs).substring(0, Patch_Margin);
                } else {
                    postcontext = diff_text1(bigpatch.diffs);
                }
                if (postcontext.length() != 0) {
                    patch.length1 += postcontext.length();
                    patch.length2 += postcontext.length();
                    if (!patch.diffs.isEmpty()
                            && patch.diffs.getLast().operation == Operation.EQUAL) {
                        patch.diffs.getLast().text += postcontext;
                    } else {
                        patch.diffs.add(new Diff(Operation.EQUAL, postcontext));
                    }
                }
                if (!empty) {
                    pointer.add(patch);
                }
            }
            bigpatch = pointer.hasNext() ? pointer.next() : null;
        }
    }

    /**
     * Take a list of patches and return a textual representation.
     *
     * @param patches List of Patch objects.
     * @return Text representation of patches.
     */
    public String patch_toText(List<Patch> patches) {
        StringBuilder text = new StringBuilder();
        for (Patch aPatch : patches) {
            text.append(aPatch);
        }
        return text.toString();
    }

    /**
     * Parse a textual representation of patches and return a List of Patch
     * objects.
     *
     * @param textline Text representation of patches.
     * @return List of Patch objects.
     * @throws IllegalArgumentException If invalid input.
     */
    public List<Patch> patch_fromText(String textline)
            throws IllegalArgumentException {
        List<Patch> patches = new LinkedList<Patch>();
        if (textline.length() == 0) {
            return patches;
        }
        List<String> textList = Arrays.asList(textline.split("\n"));
        LinkedList<String> text = new LinkedList<String>(textList);
        Patch patch;
        Pattern patchHeader
                = Pattern.compile("^@@ -(\\d+),?(\\d*) \\+(\\d+),?(\\d*) @@$");
        Matcher m;
        char sign;
        String line;
        while (!text.isEmpty()) {
            m = patchHeader.matcher(text.getFirst());
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Invalid patch string: " + text.getFirst());
            }
            patch = new Patch();
            patches.add(patch);
            patch.start1 = Integer.parseInt(m.group(1));
            if (m.group(2).length() == 0) {
                patch.start1--;
                patch.length1 = 1;
            } else if (m.group(2).equals("0")) {
                patch.length1 = 0;
            } else {
                patch.start1--;
                patch.length1 = Integer.parseInt(m.group(2));
            }

            patch.start2 = Integer.parseInt(m.group(3));
            if (m.group(4).length() == 0) {
                patch.start2--;
                patch.length2 = 1;
            } else if (m.group(4).equals("0")) {
                patch.length2 = 0;
            } else {
                patch.start2--;
                patch.length2 = Integer.parseInt(m.group(4));
            }
            text.removeFirst();

            while (!text.isEmpty()) {
                try {
                    sign = text.getFirst().charAt(0);
                } catch (IndexOutOfBoundsException e) {
                    // Blank line?  Whatever.
                    text.removeFirst();
                    continue;
                }
                line = text.getFirst().substring(1);
                line = line.replace("+", "%2B");  // decode would change all "+" to " "
                try {
                    line = URLDecoder.decode(line, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // Not likely on modern system.
                    throw new Error("This system does not support UTF-8.", e);
                } catch (IllegalArgumentException e) {
                    // Malformed URI sequence.
                    throw new IllegalArgumentException(
                            "Illegal escape in patch_fromText: " + line, e);
                }
                if (sign == '-') {
                    // Deletion.
                    patch.diffs.add(new Diff(Operation.DELETE, line));
                } else if (sign == '+') {
                    // Insertion.
                    patch.diffs.add(new Diff(Operation.INSERT, line));
                } else if (sign == ' ') {
                    // Minor equality.
                    patch.diffs.add(new Diff(Operation.EQUAL, line));
                } else if (sign == '@') {
                    // Start of next patch.
                    break;
                } else {
                    // WTF?
                    throw new IllegalArgumentException(
                            "Invalid patch mode '" + sign + "' in: " + line);
                }
                text.removeFirst();
            }
        }
        return patches;
    }


    /**
     * Class representing one diff operation.
     */
    public static class Diff {
        /**
         * One of: INSERT, DELETE or EQUAL.
         */
        public Operation operation;
        /**
         * The text associated with this diff operation.
         */
        public String text;

        public List<String> textList;

        /**
         * Constructor.  Initializes the diff with the provided values.
         *
         * @param operation One of INSERT, DELETE or EQUAL.
         * @param text      The text being applied.
         */
        public Diff(Operation operation, String text) {
            // Construct a diff with the specified operation and text.
            this.operation = operation;
            this.text = text;
        }

        public Diff(Operation operation, List<String> textList) {
            StringBuilder strBuilder = new StringBuilder();
            for (String s : textList) {
                strBuilder.append(s);
            }
            this.operation = operation;
            this.text = strBuilder.toString();
            this.textList = textList;

        }

        /**
         * Display a human-readable version of this Diff.
         *
         * @return text version.
         */
        public String toString() {
            String prettyText = this.text.replace('\n', '\u00b6');
            return "Diff(" + this.operation + ",\"" + prettyText + "\")";
        }

        /**
         * Create a numeric hash value for a Diff.
         * This function is not used by DMP.
         *
         * @return Hash value.
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = (operation == null) ? 0 : operation.hashCode();
            result += prime * ((text == null) ? 0 : text.hashCode());
            return result;
        }

        /**
         * Is this Diff equivalent to another Diff?
         *
         * @param obj Another Diff to compare against.
         * @return true or false.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Diff other = (Diff) obj;
            if (operation != other.operation) {
                return false;
            }
            if (text == null) {
                if (other.text != null) {
                    return false;
                }
            } else if (!text.equals(other.text)) {
                return false;
            }
            return true;
        }
    }


    /**
     * Class representing one patch operation.
     */
    public static class Patch {
        public LinkedList<Diff> diffs;
        public int start1;
        public int start2;
        public int length1;
        public int length2;

        /**
         * Constructor.  Initializes with an empty list of diffs.
         */
        public Patch() {
            this.diffs = new LinkedList<Diff>();
        }

        /**
         * Emmulate GNU diff's format.
         * Header: @@ -382,8 +481,9 @@
         * Indicies are printed as 1-based, not 0-based.
         *
         * @return The GNU diff string.
         */
        public String toString() {
            String coords1, coords2;
            if (this.length1 == 0) {
                coords1 = this.start1 + ",0";
            } else if (this.length1 == 1) {
                coords1 = Integer.toString(this.start1 + 1);
            } else {
                coords1 = (this.start1 + 1) + "," + this.length1;
            }
            if (this.length2 == 0) {
                coords2 = this.start2 + ",0";
            } else if (this.length2 == 1) {
                coords2 = Integer.toString(this.start2 + 1);
            } else {
                coords2 = (this.start2 + 1) + "," + this.length2;
            }
            StringBuilder text = new StringBuilder();
            text.append("@@ -").append(coords1).append(" +").append(coords2)
                    .append(" @@\n");
            // Escape the body of the patch with %xx notation.
            for (Diff aDiff : this.diffs) {
                switch (aDiff.operation) {
                    case INSERT:
                        text.append('+');
                        break;
                    case DELETE:
                        text.append('-');
                        break;
                    case EQUAL:
                        text.append(' ');
                        break;
                }
                try {
                    text.append(URLEncoder.encode(aDiff.text, "UTF-8").replace('+', ' '))
                            .append("\n");
                } catch (UnsupportedEncodingException e) {
                    // Not likely on modern system.
                    throw new Error("This system does not support UTF-8.", e);
                }
            }
            return unescapeForEncodeUriCompatability(text.toString());
        }
    }

    /**
     * Unescape selected chars for compatability with JavaScript's encodeURI.
     * In speed critical applications this could be dropped since the
     * receiving application will certainly decode these fine.
     * Note that this function is case-sensitive.  Thus "%3f" would not be
     * unescaped.  But this is ok because it is only called with the output of
     * URLEncoder.encode which returns uppercase hex.
     * <p>
     * Example: "%3F" -> "?", "%24" -> "$", etc.
     *
     * @param str The string to escape.
     * @return The escaped string.
     */
    private static String unescapeForEncodeUriCompatability(String str) {
        return str.replace("%21", "!").replace("%7E", "~")
                .replace("%27", "'").replace("%28", "(").replace("%29", ")")
                .replace("%3B", ";").replace("%2F", "/").replace("%3F", "?")
                .replace("%3A", ":").replace("%40", "@").replace("%26", "&")
                .replace("%3D", "=").replace("%2B", "+").replace("%24", "$")
                .replace("%2C", ",").replace("%23", "#");
    }

    /**
     * returns true if all elements are equal and in the same order
     */
    private boolean areEqual(List<String> list1, List<String> list2) {
        if(list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            if(!list1.get(i).equals(list2.get(i))) {
                return false;
            }
        }
        return true;
    }
}
