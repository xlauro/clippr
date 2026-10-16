package com.laurosantos.clippr.downloader;

public record TranscriptSegment(
        double startSeconds,
        double endSeconds,
        String text
) {
    public double duration() {
        return Math.max(0, endSeconds - startSeconds);
    }

    public String formattedTimeRange() {
        return "[%s -> %s]".formatted(formatSeconds(startSeconds), formatSeconds(endSeconds));
    }

    public static String formatSeconds(double seconds) {
        int totalSec = (int) seconds;
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int secs = totalSec % 60;
        int millis = (int) Math.round((seconds - totalSec) * 1000);
        return "%02d:%02d:%02d.%03d".formatted(hours, minutes, secs, millis);
    }
}
