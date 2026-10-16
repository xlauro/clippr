package com.laurosantos.clippr.domain;

import java.time.Instant;

public record VideoSource(
        Long id,
        String url,
        String videoId,
        String title,
        String author,
        double durationSeconds,
        String transcriptPath,
        String localVideoPath,
        ProcessingStatus status,
        String errorMessage,
        Instant createdAt
) {
    public static VideoSource fromUrl(String url) {
        String videoId = extractVideoId(url);
        return new VideoSource(
                null,
                url,
                videoId,
                "Aguardando informações...",
                "",
                0.0,
                null,
                null,
                ProcessingStatus.PENDING,
                null,
                Instant.now()
        );
    }

    public VideoSource withStatus(ProcessingStatus newStatus, String newError) {
        return new VideoSource(
                id, url, videoId, title, author, durationSeconds, transcriptPath, localVideoPath,
                newStatus, newError, createdAt
        );
    }

    public static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim().toLowerCase();
        return trimmed.contains("youtube.com") || trimmed.contains("youtu.be");
    }

    public static String extractVideoId(String url) {
        if (url == null || url.isBlank()) return "";
        if (url.contains("v=")) {
            String sub = url.substring(url.indexOf("v=") + 2);
            int amp = sub.indexOf('&');
            return amp != -1 ? sub.substring(0, amp) : sub;
        } else if (url.contains("youtu.be/")) {
            String sub = url.substring(url.indexOf("youtu.be/") + 9);
            int qm = sub.indexOf('?');
            return qm != -1 ? sub.substring(0, qm) : sub;
        } else if (url.contains("shorts/")) {
            String sub = url.substring(url.indexOf("shorts/") + 7);
            int qm = sub.indexOf('?');
            return qm != -1 ? sub.substring(0, qm) : sub;
        } else if (url.contains("live/")) {
            String sub = url.substring(url.indexOf("live/") + 5);
            int qm = sub.indexOf('?');
            return qm != -1 ? sub.substring(0, qm) : sub;
        }
        // Fallback: sanitiza caracteres inválidos para sistema de arquivos
        return url.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
