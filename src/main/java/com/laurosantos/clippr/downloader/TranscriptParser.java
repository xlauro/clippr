package com.laurosantos.clippr.downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranscriptParser {

    private TranscriptParser() {}

    private static final Pattern TIMESTAMP_LINE_PATTERN = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    public static List<TranscriptSegment> parseVtt(String vttContent) {
        List<TranscriptSegment> segments = new ArrayList<>();
        if (vttContent == null || vttContent.isBlank()) {
            return segments;
        }

        String[] lines = vttContent.split("\\r?\\n");
        Double currentStart = null;
        Double currentEnd = null;
        StringBuilder currentText = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("Kind:") || line.startsWith("Language:")) {
                continue;
            }

            Matcher matcher = TIMESTAMP_LINE_PATTERN.matcher(line);
            if (matcher.find()) {
                if (currentStart != null && !currentText.isEmpty()) {
                    String clean = cleanText(currentText.toString());
                    if (!clean.isBlank()) {
                        segments.add(new TranscriptSegment(currentStart, currentEnd != null ? currentEnd : currentStart, clean));
                    }
                    currentText.setLength(0);
                }

                currentStart = parseTimestampToSeconds(matcher.group(1));
                currentEnd = parseTimestampToSeconds(matcher.group(2));
            } else if (currentStart != null) {
                if (!line.matches("^\\d+$")) { // ignora números de linha
                    if (!currentText.isEmpty()) currentText.append(" ");
                    currentText.append(line);
                }
            }
        }

        if (currentStart != null && !currentText.isEmpty()) {
            String clean = cleanText(currentText.toString());
            if (!clean.isBlank()) {
                segments.add(new TranscriptSegment(currentStart, currentEnd != null ? currentEnd : currentStart, clean));
            }
        }

        return deduplicateAndMerge(segments);
    }

    public static String formatForAi(List<TranscriptSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment seg : segments) {
            sb.append(seg.formattedTimeRange())
              .append(" ")
              .append(seg.text())
              .append("\n");
        }
        return sb.toString();
    }

    private static String cleanText(String input) {
        String noTags = TAG_PATTERN.matcher(input).replaceAll("");
        return noTags.replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double parseTimestampToSeconds(String timestamp) {
        String normalized = timestamp.replace(',', '.');
        String[] parts = normalized.split(":");
        if (parts.length == 3) {
            double hours = Double.parseDouble(parts[0]);
            double minutes = Double.parseDouble(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return (hours * 3600) + (minutes * 60) + seconds;
        } else if (parts.length == 2) {
            double minutes = Double.parseDouble(parts[0]);
            double seconds = Double.parseDouble(parts[1]);
            return (minutes * 60) + seconds;
        }
        return 0.0;
    }

    private static List<TranscriptSegment> deduplicateAndMerge(List<TranscriptSegment> rawSegments) {
        List<TranscriptSegment> result = new ArrayList<>();
        TranscriptSegment previous = null;

        for (TranscriptSegment current : rawSegments) {
            if (previous == null) {
                previous = current;
                continue;
            }

            // Se o texto for igual ou continuação idêntica, funde os tempos
            if (previous.text().equalsIgnoreCase(current.text())) {
                previous = new TranscriptSegment(previous.startSeconds(), current.endSeconds(), previous.text());
            } else if (current.text().startsWith(previous.text()) && current.text().length() > previous.text().length()) {
                previous = new TranscriptSegment(previous.startSeconds(), current.endSeconds(), current.text());
            } else {
                result.add(previous);
                previous = current;
            }
        }

        if (previous != null) {
            result.add(previous);
        }

        return result;
    }
}
