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
        ProcessingStatus status
) {
    public double durationSeconds() {
        return Math.max(0, endSeconds - startSeconds);
    }

    public boolean isViralWorthy(int minScoreThreshold) {
        return viralityScore >= minScoreThreshold;
    }
}
