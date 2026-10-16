package com.laurosantos.clippr.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YtDlpSubtitleExtractionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Deve priorizar legendas manuais em português quando disponíveis")
    void shouldPrioritizeManualPortugueseSubtitles() throws Exception {
        String json = """
                {
                    "id": "test123",
                    "title": "Vídeo Teste",
                    "subtitles": {
                        "pt": [
                            {"ext": "srv1", "url": "https://example.com/srv1"},
                            {"ext": "vtt", "url": "https://example.com/manual-pt.vtt"}
                        ],
                        "en": [
                            {"ext": "vtt", "url": "https://example.com/manual-en.vtt"}
                        ]
                    },
                    "automatic_captions": {
                        "pt-orig": [
                            {"ext": "vtt", "url": "https://example.com/auto-pt.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("pt", track.language());
        assertEquals("vtt", track.format());
        assertEquals("https://example.com/manual-pt.vtt", track.url());
    }

    @Test
    @DisplayName("Deve priorizar pt-BR manual em relação ao inglês manual")
    void shouldPrioritizePtBrOverEnglish() throws Exception {
        String json = """
                {
                    "id": "test_br",
                    "subtitles": {
                        "en": [
                            {"ext": "vtt", "url": "https://example.com/en.vtt"}
                        ],
                        "pt-BR": [
                            {"ext": "vtt", "url": "https://example.com/pt-br.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("pt-BR", track.language());
        assertEquals("https://example.com/pt-br.vtt", track.url());
    }

    @Test
    @DisplayName("Deve selecionar legendas automáticas em português quando não houver legendas manuais")
    void shouldFallbackToAutoPortugueseSubtitles() throws Exception {
        String json = """
                {
                    "id": "test456",
                    "title": "Vídeo Teste Auto",
                    "subtitles": {},
                    "automatic_captions": {
                        "pt-orig": [
                            {"ext": "json3", "url": "https://example.com/json3"},
                            {"ext": "vtt", "url": "https://example.com/auto-pt-orig.vtt"}
                        ],
                        "en": [
                            {"ext": "vtt", "url": "https://example.com/auto-en.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("pt-orig", track.language());
        assertEquals("vtt", track.format());
        assertEquals("https://example.com/auto-pt-orig.vtt", track.url());
    }

    @Test
    @DisplayName("Deve escolher legenda em inglês quando não houver nenhuma versão em português")
    void shouldFallbackToEnglishWhenNoPortugueseAvailable() throws Exception {
        String json = """
                {
                    "id": "test_en",
                    "subtitles": {},
                    "automatic_captions": {
                        "en": [
                            {"ext": "vtt", "url": "https://example.com/en-auto.vtt"}
                        ],
                        "es": [
                            {"ext": "vtt", "url": "https://example.com/es-auto.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("en", track.language());
        assertEquals("https://example.com/en-auto.vtt", track.url());
    }

    @Test
    @DisplayName("Deve escolher qualquer outro idioma disponível quando não houver nem português nem inglês")
    void shouldFallbackToAnyOtherLanguageWhenNeitherPortugueseNorEnglishAvailable() throws Exception {
        String json = """
                {
                    "id": "test_other",
                    "subtitles": {
                        "es": [
                            {"ext": "vtt", "url": "https://example.com/es.vtt"}
                        ]
                    },
                    "automatic_captions": {}
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("es", track.language());
        assertEquals("https://example.com/es.vtt", track.url());
    }

    @Test
    @DisplayName("Deve priorizar formato VTT sobre SRT quando ambos estiverem disponíveis")
    void shouldPrioritizeVttOverSrt() throws Exception {
        String json = """
                {
                    "id": "test_fmt",
                    "subtitles": {
                        "pt": [
                            {"ext": "srt", "url": "https://example.com/pt.srt"},
                            {"ext": "vtt", "url": "https://example.com/pt.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("vtt", track.format());
        assertEquals("https://example.com/pt.vtt", track.url());
    }

    @Test
    @DisplayName("Deve selecionar formato SRT se VTT não estiver disponível")
    void shouldSelectSrtWhenVttNotAvailable() throws Exception {
        String json = """
                {
                    "id": "test_srt",
                    "subtitles": {
                        "pt": [
                            {"ext": "srv1", "url": "https://example.com/pt.srv1"},
                            {"ext": "srt", "url": "https://example.com/pt.srt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("srt", track.format());
        assertEquals("https://example.com/pt.srt", track.url());
    }

    @Test
    @DisplayName("Deve retornar nulo quando apenas formatos não suportados (ex: json3, ttml) estiverem disponíveis")
    void shouldReturnNullWhenOnlyUnsupportedFormatsAvailable() throws Exception {
        String json = """
                {
                    "id": "test_unsupported",
                    "subtitles": {
                        "pt": [
                            {"ext": "json3", "url": "https://example.com/pt.json3"},
                            {"ext": "ttml", "url": "https://example.com/pt.ttml"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNull(track);
    }

    @Test
    @DisplayName("Deve ignorar entradas com URL em branco ou nula")
    void shouldSkipEntriesWithBlankOrMissingUrl() throws Exception {
        String json = """
                {
                    "id": "test_blank_url",
                    "subtitles": {
                        "pt": [
                            {"ext": "vtt", "url": ""},
                            {"ext": "vtt", "url": "   "}
                        ],
                        "en": [
                            {"ext": "vtt", "url": "https://example.com/en.vtt"}
                        ]
                    }
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNotNull(track);
        assertEquals("en", track.language());
        assertEquals("https://example.com/en.vtt", track.url());
    }

    @Test
    @DisplayName("Deve lidar com segurança quando root for nulo ou objeto vazio")
    void shouldHandleNullOrEmptyRootSafely() throws Exception {
        assertNull(YtDlpManager.findBestSubtitleTrack(null));

        var emptyRoot = MAPPER.readTree("{}");
        assertNull(YtDlpManager.findBestSubtitleTrack(emptyRoot));
    }

    @Test
    @DisplayName("Deve retornar nulo quando não houver nenhuma legenda disponível")
    void shouldReturnNullWhenNoSubtitlesAvailable() throws Exception {
        String json = """
                {
                    "id": "test789",
                    "title": "Sem Legendas",
                    "subtitles": {},
                    "automatic_captions": {}
                }
                """;

        var root = MAPPER.readTree(json);
        var track = YtDlpManager.findBestSubtitleTrack(root);

        assertNull(track);
    }

    @Test
    @DisplayName("Deve extrair com sucesso metadados e transcrição do vídeo informado pelo usuário")
    void shouldFetchMetadataAndTranscriptForRealVideo() throws Exception {
        YtDlpManager manager = new YtDlpManager();
        var result = manager.fetchMetadataAndTranscript("https://youtu.be/UA6iCAUwKH8");

        assertNotNull(result);
        assertEquals("UA6iCAUwKH8", result.metadata().id());
        assertFalse(result.metadata().title().isBlank());
        assertTrue(result.metadata().durationSeconds() > 0);
        assertNotNull(result.vttFile());
        assertFalse(result.segments().isEmpty(), "O vídeo deve possuir segmentos de transcrição extraídos");
        assertFalse(result.formattedTranscript().isBlank(), "A transcrição formatada para IA não deve estar em branco");
    }
}
