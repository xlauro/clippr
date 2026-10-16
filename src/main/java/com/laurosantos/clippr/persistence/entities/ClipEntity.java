package com.laurosantos.clippr.persistence.entities;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clips")
public class ClipEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_source_id", nullable = false)
    private VideoEntity videoSource;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String hook;

    @Column(name = "start_seconds", nullable = false)
    private double startSeconds;

    @Column(name = "end_seconds", nullable = false)
    private double endSeconds;

    @Column(name = "virality_score", nullable = false)
    private int viralityScore;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "suggested_caption", columnDefinition = "TEXT")
    private String suggestedCaption;

    @Column(name = "hashtags_json", columnDefinition = "TEXT")
    private String hashtagsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrientationMode orientation = OrientationMode.AUTO;

    @Column(name = "has_subtitles", nullable = false)
    private boolean hasSubtitles = true;

    @Column(name = "output_file_path", length = 1000)
    private String outputFilePath;

    @Column(name = "metadata_json_path", length = 1000)
    private String metadataJsonPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcessingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ClipEntity() {}

    public static ClipEntity fromDomain(ClipCandidate domain, VideoEntity parentEntity) {
        ClipEntity entity = new ClipEntity();
        entity.id = domain.id();
        entity.videoSource = parentEntity;
        entity.title = domain.title();
        entity.hook = domain.hook();
        entity.startSeconds = domain.startSeconds();
        entity.endSeconds = domain.endSeconds();
        entity.viralityScore = domain.viralityScore();
        entity.reasoning = domain.reasoning();
        entity.suggestedCaption = domain.suggestedCaption();
        entity.orientation = domain.orientation() != null ? domain.orientation() : OrientationMode.AUTO;
        entity.hasSubtitles = domain.hasSubtitles();
        entity.outputFilePath = domain.outputFilePath();
        entity.metadataJsonPath = domain.metadataJsonPath();
        entity.status = domain.status();
        try {
            entity.hashtagsJson = MAPPER.writeValueAsString(domain.hashtags() != null ? domain.hashtags() : List.of());
        } catch (Exception e) {
            entity.hashtagsJson = "[]";
        }
        return entity;
    }

    public ClipCandidate toDomain() {
        List<String> tags = new ArrayList<>();
        if (hashtagsJson != null && !hashtagsJson.isBlank()) {
            try {
                tags = MAPPER.readValue(hashtagsJson, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }

        return new ClipCandidate(
                id,
                videoSource != null ? videoSource.getId() : null,
                title,
                hook,
                startSeconds,
                endSeconds,
                viralityScore,
                reasoning,
                suggestedCaption,
                tags,
                orientation,
                hasSubtitles,
                outputFilePath,
                metadataJsonPath,
                status
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VideoEntity getVideoSource() { return videoSource; }
    public void setVideoSource(VideoEntity videoSource) { this.videoSource = videoSource; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHook() { return hook; }
    public void setHook(String hook) { this.hook = hook; }

    public double getStartSeconds() { return startSeconds; }
    public void setStartSeconds(double startSeconds) { this.startSeconds = startSeconds; }

    public double getEndSeconds() { return endSeconds; }
    public void setEndSeconds(double endSeconds) { this.endSeconds = endSeconds; }

    public int getViralityScore() { return viralityScore; }
    public void setViralityScore(int viralityScore) { this.viralityScore = viralityScore; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getSuggestedCaption() { return suggestedCaption; }
    public void setSuggestedCaption(String suggestedCaption) { this.suggestedCaption = suggestedCaption; }

    public String getHashtagsJson() { return hashtagsJson; }
    public void setHashtagsJson(String hashtagsJson) { this.hashtagsJson = hashtagsJson; }

    public OrientationMode getOrientation() { return orientation; }
    public void setOrientation(OrientationMode orientation) { this.orientation = orientation; }

    public boolean isHasSubtitles() { return hasSubtitles; }
    public void setHasSubtitles(boolean hasSubtitles) { this.hasSubtitles = hasSubtitles; }

    public String getOutputFilePath() { return outputFilePath; }
    public void setOutputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; }

    public String getMetadataJsonPath() { return metadataJsonPath; }
    public void setMetadataJsonPath(String metadataJsonPath) { this.metadataJsonPath = metadataJsonPath; }

    public ProcessingStatus getStatus() { return status; }
    public void setStatus(ProcessingStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
