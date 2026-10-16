package com.laurosantos.clippr.persistence.entities;

import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.domain.VideoSource;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "video_sources")
public class VideoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "video_id", length = 100)
    private String videoId;

    @Column(length = 500)
    private String title;

    @Column(length = 200)
    private String author;

    @Column(name = "duration_seconds")
    private double durationSeconds;

    @Column(name = "transcript_path", length = 1000)
    private String transcriptPath;

    @Column(name = "local_video_path", length = 1000)
    private String localVideoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcessingStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "videoSource", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClipEntity> clips = new ArrayList<>();

    public VideoEntity() {}

    public static VideoEntity fromDomain(VideoSource domain) {
        VideoEntity entity = new VideoEntity();
        entity.id = domain.id();
        entity.url = domain.url();
        entity.videoId = domain.videoId();
        entity.title = domain.title();
        entity.author = domain.author();
        entity.durationSeconds = domain.durationSeconds();
        entity.transcriptPath = domain.transcriptPath();
        entity.localVideoPath = domain.localVideoPath();
        entity.status = domain.status();
        entity.errorMessage = domain.errorMessage();
        entity.createdAt = domain.createdAt() != null ? domain.createdAt() : Instant.now();
        return entity;
    }

    public VideoSource toDomain() {
        return new VideoSource(
                id,
                url,
                videoId,
                title,
                author,
                durationSeconds,
                transcriptPath,
                localVideoPath,
                status,
                errorMessage,
                createdAt
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getTranscriptPath() { return transcriptPath; }
    public void setTranscriptPath(String transcriptPath) { this.transcriptPath = transcriptPath; }

    public String getLocalVideoPath() { return localVideoPath; }
    public void setLocalVideoPath(String localVideoPath) { this.localVideoPath = localVideoPath; }

    public ProcessingStatus getStatus() { return status; }
    public void setStatus(ProcessingStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<ClipEntity> getClips() { return clips; }
    public void setClips(List<ClipEntity> clips) { this.clips = clips; }
}
