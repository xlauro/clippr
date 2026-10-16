package com.laurosantos.clippr.persistence.entities;

import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.OrientationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_settings")
public class SettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "output_directory", length = 1000)
    private String outputDirectory;

    @Column(name = "min_virality_score")
    private int minViralityScore = 90;

    @Column(name = "auto_process")
    private boolean autoProcessAboveThreshold = false;

    @Column(name = "default_orientation", length = 30)
    private String defaultOrientation = OrientationMode.AUTO.name();

    @Column(name = "auto_vertical_max_duration")
    private double autoVerticalMaxDurationSeconds = 60.0;

    @Column(name = "burn_subtitles")
    private boolean burnSubtitles = true;

    @Column(name = "cpu_threads")
    private int cpuThreads = 0;

    @Column(name = "gemini_api_key", length = 500)
    private String geminiApiKey = "";

    @Column(name = "gemini_model", length = 100)
    private String geminiModel = "gemini-2.5-flash";

    public SettingsEntity() {}

    public static SettingsEntity fromDomain(AppSettings settings) {
        SettingsEntity entity = new SettingsEntity();
        entity.outputDirectory = settings.outputDirectory();
        entity.minViralityScore = settings.minViralityScore();
        entity.autoProcessAboveThreshold = settings.autoProcessAboveThreshold();
        entity.defaultOrientation = settings.defaultOrientation().name();
        entity.autoVerticalMaxDurationSeconds = settings.autoVerticalMaxDurationSeconds();
        entity.burnSubtitles = settings.burnSubtitles();
        entity.cpuThreads = settings.cpuThreads();
        entity.geminiApiKey = settings.geminiApiKey();
        entity.geminiModel = settings.geminiModel();
        return entity;
    }

    public AppSettings toDomain() {
        OrientationMode mode = OrientationMode.AUTO;
        try {
            if (defaultOrientation != null) mode = OrientationMode.valueOf(defaultOrientation);
        } catch (Exception ignored) {}

        return new AppSettings(
                outputDirectory != null ? outputDirectory : AppSettings.defaultSettings().outputDirectory(),
                minViralityScore,
                autoProcessAboveThreshold,
                mode,
                autoVerticalMaxDurationSeconds,
                burnSubtitles,
                cpuThreads,
                geminiApiKey != null ? geminiApiKey : "",
                geminiModel != null ? geminiModel : "gemini-2.5-flash"
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }

    public int getMinViralityScore() { return minViralityScore; }
    public void setMinViralityScore(int minViralityScore) { this.minViralityScore = minViralityScore; }

    public boolean isAutoProcessAboveThreshold() { return autoProcessAboveThreshold; }
    public void setAutoProcessAboveThreshold(boolean autoProcessAboveThreshold) { this.autoProcessAboveThreshold = autoProcessAboveThreshold; }

    public String getDefaultOrientation() { return defaultOrientation; }
    public void setDefaultOrientation(String defaultOrientation) { this.defaultOrientation = defaultOrientation; }

    public double getAutoVerticalMaxDurationSeconds() { return autoVerticalMaxDurationSeconds; }
    public void setAutoVerticalMaxDurationSeconds(double autoVerticalMaxDurationSeconds) { this.autoVerticalMaxDurationSeconds = autoVerticalMaxDurationSeconds; }

    public boolean isBurnSubtitles() { return burnSubtitles; }
    public void setBurnSubtitles(boolean burnSubtitles) { this.burnSubtitles = burnSubtitles; }

    public int getCpuThreads() { return cpuThreads; }
    public void setCpuThreads(int cpuThreads) { this.cpuThreads = cpuThreads; }

    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }

    public String getGeminiModel() { return geminiModel; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
}
