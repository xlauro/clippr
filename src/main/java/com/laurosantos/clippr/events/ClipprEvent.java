package com.laurosantos.clippr.events;

import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.VideoSource;

import java.time.Instant;

public sealed interface ClipprEvent {
    Instant timestamp();

    record LogMessage(Instant timestamp, String message, boolean isDebug) implements ClipprEvent {
        public LogMessage(String message, boolean isDebug) {
            this(Instant.now(), message, isDebug);
        }
    }

    record ProgressUpdated(Instant timestamp, String stage, double progress, String details) implements ClipprEvent {
        public ProgressUpdated(String stage, double progress, String details) {
            this(Instant.now(), stage, progress, details);
        }

        public ProgressUpdated(double progress, String details) {
            this(Instant.now(), "PIPELINE", progress, details);
        }
    }

    record VideoSourceUpdated(Instant timestamp, VideoSource videoSource) implements ClipprEvent {
        public VideoSourceUpdated(VideoSource videoSource) {
            this(Instant.now(), videoSource);
        }
    }

    record ClipsEvaluated(Instant timestamp, Long videoSourceId, java.util.List<ClipCandidate> approvedClips, int discardedCount) implements ClipprEvent {
        public ClipsEvaluated(Long videoSourceId, java.util.List<ClipCandidate> approvedClips, int discardedCount) {
            this(Instant.now(), videoSourceId, approvedClips, discardedCount);
        }
    }

    record ClipRenderCompleted(Instant timestamp, ClipCandidate clip) implements ClipprEvent {
        public ClipRenderCompleted(ClipCandidate clip) {
            this(Instant.now(), clip);
        }
    }

    record PipelineError(Instant timestamp, String stage, String errorMessage, Throwable cause) implements ClipprEvent {
        public PipelineError(String stage, String errorMessage, Throwable cause) {
            this(Instant.now(), stage, errorMessage, cause);
        }
    }
}
