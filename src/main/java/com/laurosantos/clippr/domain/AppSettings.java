package com.laurosantos.clippr.domain;

import java.nio.file.Path;

public record AppSettings(
        String outputDirectory,
        int minViralityScore,
        boolean autoProcessAboveThreshold,
        OrientationMode defaultOrientation,
        double autoVerticalMaxDurationSeconds,
        boolean burnSubtitles,
        int cpuThreads,
        String geminiApiKey,
        String geminiModel
) {
    public static AppSettings defaultSettings() {
        String homeDir = System.getProperty("user.home", ".");
        String defaultOutput = Path.of(homeDir, "ClipprExports").toString();
        String apiKey = System.getenv().getOrDefault("GEMINI_API_KEY", "");
        return new AppSettings(
                defaultOutput,
                90,
                false,
                OrientationMode.AUTO,
                60.0,
                true,
                0, // 0 = 100% CPU (FFmpeg threads 0)
                apiKey,
                "gemini-2.5-flash" // compatível com a API padrão do Gemini
        );
    }
}
