package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.VideoSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class VideoSourceTest {

    @ParameterizedTest
    @DisplayName("Deve extrair videoId corretamente de múltiplos formatos de URLs do YouTube")
    @CsvSource({
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&ab_channel=RickAstley, dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ, dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abcdef12345, dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/3xyz987Abc, 3xyz987Abc",
            "https://www.youtube.com/shorts/3xyz987Abc?feature=share, 3xyz987Abc"
    })
    void shouldExtractVideoIdCorrectly(String url, String expectedId) {
        String videoId = VideoSource.extractVideoId(url);
        assertEquals(expectedId, videoId);
    }

    @Test
    @DisplayName("Deve lidar graciosamente com URLs vazias ou nulas")
    void shouldHandleNullOrBlankUrls() {
        assertEquals("", VideoSource.extractVideoId(null));
        assertEquals("", VideoSource.extractVideoId("   "));
    }

    @Test
    @DisplayName("fromUrl deve criar VideoSource com valores iniciais coerentes")
    void shouldCreateInitialVideoSourceFromUrl() {
        VideoSource source = VideoSource.fromUrl("https://www.youtube.com/watch?v=teste123");
        assertNull(source.id());
        assertEquals("teste123", source.videoId());
        assertNotNull(source.createdAt());
        assertEquals(0.0, source.durationSeconds());
    }

    @ParameterizedTest
    @DisplayName("isValidYouTubeUrl deve validar links do YouTube corretamente")
    @CsvSource({
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ, true",
            "https://youtu.be/dQw4w9WgXcQ, true",
            "https://youtube.com/shorts/123, true",
            "https://vimeo.com/123456, false",
            "not-a-url, false",
            "'', false"
    })
    void shouldValidateYouTubeUrls(String url, boolean expected) {
        assertEquals(expected, VideoSource.isValidYouTubeUrl(url));
    }

    @Test
    @DisplayName("withStatus deve atualizar status de forma imutável")
    void shouldUpdateStatusImmutably() {
        VideoSource source = VideoSource.fromUrl("https://www.youtube.com/watch?v=teste123");
        VideoSource updated = source.withStatus(com.laurosantos.clippr.domain.ProcessingStatus.COMPLETED, null);
        assertEquals(com.laurosantos.clippr.domain.ProcessingStatus.COMPLETED, updated.status());
        assertEquals(com.laurosantos.clippr.domain.ProcessingStatus.PENDING, source.status());
    }
}
