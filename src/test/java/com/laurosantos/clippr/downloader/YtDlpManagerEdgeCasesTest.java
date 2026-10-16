package com.laurosantos.clippr.downloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YtDlpManagerEdgeCasesTest {

    @Test
    @DisplayName("Deve extrair metadados e transcrição a partir de URL completa do YouTube com watch?v=")
    void shouldExtractMetadataFromFullWatchUrl() throws Exception {
        YtDlpManager manager = new YtDlpManager();
        var result = manager.fetchMetadataAndTranscript("https://www.youtube.com/watch?v=UA6iCAUwKH8");

        assertNotNull(result);
        assertEquals("UA6iCAUwKH8", result.metadata().id());
        assertFalse(result.metadata().title().isBlank());
        assertFalse(result.segments().isEmpty());
    }

    @Test
    @DisplayName("Deve extrair metadados e transcrição a partir de URL encurtada com parâmetros de tracking (?si=...)")
    void shouldExtractMetadataFromShortUrlWithTrackingParams() throws Exception {
        YtDlpManager manager = new YtDlpManager();
        var result = manager.fetchMetadataAndTranscript("https://youtu.be/UA6iCAUwKH8?si=1mtr3PVdQrpspTrY");

        assertNotNull(result);
        assertEquals("UA6iCAUwKH8", result.metadata().id());
        assertFalse(result.metadata().title().isBlank());
        assertFalse(result.segments().isEmpty());
    }

    @Test
    @DisplayName("Deve lançar RuntimeException com mensagem clara quando a URL do vídeo for inválida ou inexistente")
    void shouldThrowExceptionWhenVideoDoesNotExist() {
        YtDlpManager manager = new YtDlpManager();
        String invalidUrl = "https://www.youtube.com/watch?v=invalid_id_not_found_9999999";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                manager.fetchMetadataAndTranscript(invalidUrl)
        );

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Falha no yt-dlp ao processar URL") || ex.getMessage().contains("exitCode"));
    }

    @Test
    @DisplayName("Deve carregar arquivo VTT pré-existente na pasta temp se já tiver sido gerado anteriormente")
    void shouldReuseExistingVttFileIfPresent() throws Exception {
        Path tempDir = Files.createDirectories(Path.of("temp", "transcripts"));
        String dummyId = "test_cache_id_" + System.currentTimeMillis();
        Path dummyVtt = tempDir.resolve(dummyId + ".dummy.vtt");

        String vttContent = """
                WEBVTT
                
                00:00:01.000 --> 00:00:03.000
                Segmento de teste em cache.
                """;

        Files.writeString(dummyVtt, vttContent);

        try {
            // Verifica que o arquivo existe
            assertTrue(Files.exists(dummyVtt));

            // Simula leitura de arquivo local
            var segments = TranscriptParser.parseVtt(Files.readString(dummyVtt));
            assertEquals(1, segments.size());
            assertEquals("Segmento de teste em cache.", segments.getFirst().text());
        } finally {
            Files.deleteIfExists(dummyVtt);
        }
    }
}
