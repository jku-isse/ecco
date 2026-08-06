package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Michael Jahn
 */
public class SampleReaderService {

    public List<String> readSamplesFromFile(String filePath, List<String> sampleSeparator, boolean sampleStopOnLineBreak) throws IOException {

        // TODO parsing the whole file into one string and using the readSamplesFromString method would be safer but slower

        List<String> samples = new ArrayList<>();
        StringBuilder sampleBuilder = new StringBuilder();

        File file = new File(filePath);
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        while ((line = br.readLine()) != null) {
            sampleBuilder.append(line);
            for (String seperator : sampleSeparator) {
                if (sampleBuilder.toString().endsWith(seperator) || sampleStopOnLineBreak) {
//                    samples.addAll(Arrays.asList(sampleBuilder.toString().split(Pattern.quote(seperator))).stream().map(s -> s + sampleSeparator).collect(toList()));
                    samples.add(sampleBuilder.toString());
                    sampleBuilder.setLength(0);
                }
            }
        }
        br.close();
        fr.close();

        return samples;
    }

    public List<String> readSamplesFromString(String input, List<String> sampleSeparator, List<String> commentIdentifiers, boolean sampleStopOnLineBreak) {
        List<String> samples = new ArrayList<>();
        StringBuilder curString = new StringBuilder(input);

        List<Pattern> commentPatterns = commentIdentifiers.stream().map(s -> Pattern.compile(s,  Pattern.DOTALL | Pattern.MULTILINE)).collect(Collectors.toList());
        List<Pattern> sampleSeparatorPatterns = sampleSeparator.stream().map(s -> Pattern.compile(s + (sampleStopOnLineBreak ? "$" : ""), Pattern.MULTILINE)).collect(Collectors.toList());

        // filter out comments
        for (Pattern commentPattern : commentPatterns) {
            Matcher matcher = commentPattern.matcher(curString);
            if(matcher.find()) {
                curString = new StringBuilder(matcher.replaceAll(""));
            }
        }

        while (!curString.toString().equals("")) {

            Map<Integer, Pattern> foundSampleEndMap = new HashMap<>();
            // find first fitting pattern
            for (Pattern sampleSaparatorPattern : sampleSeparatorPatterns) {
                Matcher matcher = sampleSaparatorPattern.matcher(curString);
                if (matcher.find()) {
                    foundSampleEndMap.put(matcher.start(), sampleSaparatorPattern);
                }
            }
            if (foundSampleEndMap.size() >= 1) {
                int minIdx = Collections.min(foundSampleEndMap.keySet());
                Pattern minPattern = foundSampleEndMap.get(minIdx);

                Matcher matcher = minPattern.matcher(curString);
                if (matcher.find()) {
                    String sample = curString.substring(0, matcher.end()).trim();
                    samples.add(sample);
                    curString.delete(0, matcher.end());
                } else {
                    // no patterns matching any more -> add rest of the string to the samples list
                    samples.add(curString.toString());
                    curString.setLength(0);
                }
            } else {
                samples.add(curString.toString());
                curString.setLength(0);
            }
        }

        return samples;
    }
}
