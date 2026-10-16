package com.laurosantos.clippr.domain;

import java.util.List;

public record ClipCandidate(
        Long id,
        Long videoSourceId,
        String title,
        String hook,
        double startSeconds,
        double endSeconds,
        int viralityScore,
        String reasoning,
        String suggestedCaption,
        List<String> hashtags,
        OrientationMode orientation,
        boolean hasSubtitles,
        String outputFilePath,
        String metadataJsonPath,
        ProcessingStatus status,
        Double thumbnailTimestamp,
        String thumbnailHeadline,
        String thumbnailPath,
        String rawFramePath
) {
    public ClipCandidate(
            Long id,
            Long videoSourceId,
            String title,
            String hook,
            double startSeconds,
            double endSeconds,
            int viralityScore,
            String reasoning,
            String suggestedCaption,
            List<String> hashtags,
            OrientationMode orientation,
            boolean hasSubtitles,
            String outputFilePath,
            String metadataJsonPath,
            ProcessingStatus status
    ) {
        this(
                id, videoSourceId, title, hook, startSeconds, endSeconds, viralityScore,
                reasoning, suggestedCaption, hashtags, orientation, hasSubtitles,
                outputFilePath, metadataJsonPath, status,
                null, null, null, null
        );
    }
    public double durationSeconds() {
        return Math.max(0, endSeconds - startSeconds);
    }

    public boolean isViralWorthy(int minScoreThreshold) {
        return viralityScore >= minScoreThreshold;
    }

    public boolean isLongClip() {
        return durationSeconds() > 90.0;
    }

    public String clipTypeBadge() {
        return isLongClip() ? "🎬 Longo" : "⚡ Curto";
    }

    public String formattedDuration() {
        int totalSec = (int) Math.round(durationSeconds());
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int secs = totalSec % 60;

        if (hours > 0) {
            return "%02dh %02dm %02ds".formatted(hours, minutes, secs);
        } else if (minutes > 0) {
            return "%02dm %02ds".formatted(minutes, secs);
        } else {
            return "%ds".formatted(secs);
        }
    }

    public String formattedTimeRange() {
        boolean hasHours = (startSeconds >= 3600 || endSeconds >= 3600);
        return "%s - %s".formatted(formatTimestamp(startSeconds, hasHours), formatTimestamp(endSeconds, hasHours));
    }

    public String formattedDisplayInterval() {
        return "%s (%s)".formatted(formattedTimeRange(), formattedDuration());
    }

    private static String formatTimestamp(double seconds, boolean hasHours) {
        int totalSec = Math.max(0, (int) Math.floor(seconds));
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int secs = totalSec % 60;

        if (hasHours || hours > 0) {
            return "%02d:%02d:%02d".formatted(hours, minutes, secs);
        }
        return "%02d:%02d".formatted(minutes, secs);
    }

    public ClipCandidate withBounds(double newStart, double newEnd) {
        return new ClipCandidate(
                id, videoSourceId, title, hook, newStart, newEnd, viralityScore,
                reasoning, suggestedCaption, hashtags, orientation, hasSubtitles,
                outputFilePath, metadataJsonPath, status,
                thumbnailTimestamp, thumbnailHeadline, thumbnailPath, rawFramePath
        );
    }

    public ClipCandidate withThumbnail(Double timestamp, String headline, String newThumbnailPath, String newRawFramePath) {
        return new ClipCandidate(
                id, videoSourceId, title, hook, startSeconds, endSeconds, viralityScore,
                reasoning, suggestedCaption, hashtags, orientation, hasSubtitles,
                outputFilePath, metadataJsonPath, status,
                timestamp, headline, newThumbnailPath, newRawFramePath
        );
    }

    public ClipCandidate withThumbnail(String newThumbnailPath, String newRawFramePath) {
        return withThumbnail(thumbnailTimestamp, thumbnailHeadline, newThumbnailPath, newRawFramePath);
    }

    public boolean hasThumbnail() {
        return thumbnailPath != null && !thumbnailPath.isBlank();
    }
}
