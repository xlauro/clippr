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

    @Test
    @DisplayName("withBounds deve criar nova instância de ClipCandidate com timestamps atualizados preservando os demais atributos")
    void shouldUpdateBoundsPreservingOtherAttributes() {
        ClipCandidate original = new ClipCandidate(
                10L, 2L, "Título", "Gancho", 15.0, 45.0, 95, "Motivo", "Legenda",
                List.of("tag1"), OrientationMode.VERTICAL_9_16, true, "/path.mp4", "/path.json", ProcessingStatus.PENDING_APPROVAL
        );

        ClipCandidate updated = original.withBounds(14.0, 46.5);

        assertEquals(10L, updated.id());
        assertEquals(2L, updated.videoSourceId());
        assertEquals("Título", updated.title());
        assertEquals(14.0, updated.startSeconds(), 0.001);
        assertEquals(46.5, updated.endSeconds(), 0.001);
        assertEquals(32.5, updated.durationSeconds(), 0.001);
        assertEquals(95, updated.viralityScore());
    }

    private ClipCandidate createClipWithScore(int score) {
        return new ClipCandidate(
                1L, 1L, "Título", "Gancho", 0.0, 30.0, score,
                "Reasoning", "Caption", List.of(), OrientationMode.VERTICAL_9_16,
                true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
    }

    @Test
    @DisplayName("isLongClip e clipTypeBadge devem identificar corretamente cortes curtos e longos (> 90s)")
    void shouldIdentifyLongAndShortClips() {
        ClipCandidate shortClip = new ClipCandidate(
                1L, 1L, "Curto", "H", 0.0, 90.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertFalse(shortClip.isLongClip());
        assertEquals("⚡ Curto", shortClip.clipTypeBadge());

        ClipCandidate longClip = new ClipCandidate(
                2L, 1L, "Longo", "H", 0.0, 90.1, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertTrue(longClip.isLongClip());
        assertEquals("🎬 Longo", longClip.clipTypeBadge());

        ClipCandidate deepDive = new ClipCandidate(
                3L, 1L, "Deep Dive", "H", 120.0, 720.0, 95, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertTrue(deepDive.isLongClip());
        assertEquals("🎬 Longo", deepDive.clipTypeBadge());
    }

    @Test
    @DisplayName("formattedDuration deve formatar tempos inferiores a 1 minuto, em minutos e em horas")
    void shouldFormatDurationCorrectly() {
        ClipCandidate secondsOnly = new ClipCandidate(
                1L, 1L, "T", "H", 0.0, 45.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals("45s", secondsOnly.formattedDuration());

        ClipCandidate minutesClip = new ClipCandidate(
                2L, 1L, "T", "H", 0.0, 135.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals("02m 15s", minutesClip.formattedDuration());

        ClipCandidate hoursClip = new ClipCandidate(
                3L, 1L, "T", "H", 0.0, 3912.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals("01h 05m 12s", hoursClip.formattedDuration());
    }

    @Test
    @DisplayName("formattedTimeRange e formattedDisplayInterval devem exibir intervalos no formato MM:SS e HH:MM:SS com duração")
    void shouldFormatTimeRangeAndDisplayInterval() {
        // 02:30 a 12:45 -> 150s a 765s (duração: 615s = 10m 15s)
        ClipCandidate clip = new ClipCandidate(
                1L, 1L, "T", "H", 150.0, 765.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals("02:30 - 12:45", clip.formattedTimeRange());
        assertEquals("02:30 - 12:45 (10m 15s)", clip.formattedDisplayInterval());

        // Com mais de 1 hora: 01:02:30 (3750s) a 01:12:45 (4365s) -> duração: 615s (10m 15s)
        ClipCandidate hourClip = new ClipCandidate(
                2L, 1L, "T", "H", 3750.0, 4365.0, 90, "R", "C", List.of(),
                OrientationMode.VERTICAL_9_16, true, null, null, ProcessingStatus.PENDING_APPROVAL
        );
        assertEquals("01:02:30 - 01:12:45", hourClip.formattedTimeRange());
        assertEquals("01:02:30 - 01:12:45 (10m 15s)", hourClip.formattedDisplayInterval());
    }

    @Test
    @DisplayName("withThumbnail deve atualizar atributos de capa e hasThumbnail deve retornar true")
    void shouldSupportThumbnailPropertiesAndWithThumbnail() {
        ClipCandidate original = new ClipCandidate(
                1L, 1L, "Título", "Gancho", 10.0, 300.0, 95, "Reasoning", "Caption",
                List.of(), OrientationMode.HORIZONTAL_16_9, false, null, null,
                ProcessingStatus.PENDING_APPROVAL
        );

        assertNull(original.thumbnailTimestamp());
        assertNull(original.thumbnailHeadline());
        assertNull(original.thumbnailPath());
        assertNull(original.rawFramePath());
        assertFalse(original.hasThumbnail());

        ClipCandidate withThumb = original.withThumbnail(
                45.5,
                "O SEGREDO REVELADO",
                "/tmp/thumb.jpg",
                "/tmp/raw_frame.jpg"
        );

        assertEquals(45.5, withThumb.thumbnailTimestamp());
        assertEquals("O SEGREDO REVELADO", withThumb.thumbnailHeadline());
        assertEquals("/tmp/thumb.jpg", withThumb.thumbnailPath());
        assertEquals("/tmp/raw_frame.jpg", withThumb.rawFramePath());
        assertTrue(withThumb.hasThumbnail());

        // withBounds deve preservar thumbnail
        ClipCandidate bounded = withThumb.withBounds(12.0, 290.0);
        assertEquals(45.5, bounded.thumbnailTimestamp());
        assertEquals("O SEGREDO REVELADO", bounded.thumbnailHeadline());
        assertEquals("/tmp/thumb.jpg", bounded.thumbnailPath());
        assertEquals("/tmp/raw_frame.jpg", bounded.rawFramePath());
        assertTrue(bounded.hasThumbnail());
    }
}
