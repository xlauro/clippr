package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipCandidateTest {

    @Test
    @DisplayName("isViralWorthy deve validar corretamente o limiar de pontuação")
    void shouldCheckViralityScoreThreshold() {
        ClipCandidate c1 = createClipWithScore(95);
        assertTrue(c1.isViralWorthy(90));
        assertTrue(c1.isViralWorthy(95));
        assertFalse(c1.isViralWorthy(96));

        ClipCandidate c2 = createClipWithScore(89);
        assertFalse(c2.isViralWorthy(90));
    }

    @Test
    @DisplayName("durationSeconds deve calcular a diferença de tempo ou retornar 0 se inválido")
    void shouldCalculateDurationCorrectly() {
        ClipCandidate valid = new ClipCandidate(
                1L, 1L, "T", "H", 10.5, 30.5, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals(20.0, valid.durationSeconds(), 0.001);

        ClipCandidate inverted = new ClipCandidate(
                1L, 1L, "T", "H", 30.0, 10.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals(0.0, inverted.durationSeconds(), 0.001);
    }

    private ClipCandidate createClipWithScore(int score) {
        return new ClipCandidate(
                1L, 1L, "Título", "Gancho", 0.0, 30.0, score,
                "Reasoning", "Caption", List.of(), OrientationMode.VERTICAL_9_16,
                true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
    }
}
