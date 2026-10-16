package com.laurosantos.clippr.domain;

public enum OrientationMode {
    AUTO("Automático (Vídeos < 1min = 9:16 Vertical, outros = 16:9)"),
    VERTICAL_9_16("Retrato Vertical (9:16 - TikTok/Reels/Shorts)"),
    HORIZONTAL_16_9("Paisagem Horizontal (16:9 - YouTube Padrão)");

    private final String label;

    OrientationMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static OrientationMode resolveForDuration(OrientationMode configuredMode, double durationSeconds, double autoThresholdSeconds) {
        if (configuredMode == AUTO) {
            return durationSeconds <= autoThresholdSeconds ? VERTICAL_9_16 : HORIZONTAL_16_9;
        }
        return configuredMode;
    }
}
