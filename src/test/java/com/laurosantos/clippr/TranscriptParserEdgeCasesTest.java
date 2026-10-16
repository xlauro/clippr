package com.laurosantos.clippr;

import com.laurosantos.clippr.downloader.TranscriptParser;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptParserEdgeCasesTest {

    @Test
    @DisplayName("Deve ignorar cabeçalhos WEBVTT e metadados no início do arquivo")
    void shouldIgnoreHeadersAndNotes() {
        String vtt = """
                WEBVTT
                Kind: captions
                Language: pt-BR
                NOTE Este arquivo foi gerado automaticamente

                00:00:05,000 --> 00:00:10,000
                Primeira fala real.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);
        assertEquals(1, segments.size());
        assertEquals("Primeira fala real.", segments.getFirst().text());
        assertEquals(5.0, segments.getFirst().startSeconds(), 0.001);
        assertEquals(10.0, segments.getFirst().endSeconds(), 0.001);
    }

    @Test
    @DisplayName("Deve lidar com timestamps sem hora (apenas MM:SS.mmm)")
    void shouldHandleTimestampsWithoutHours() {
        String vtt = """
                WEBVTT

                01:30.500 --> 01:35.000
                Fala em um minuto e trinta segundos.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);
        assertEquals(1, segments.size());
        assertEquals(90.5, segments.getFirst().startSeconds(), 0.001);
        assertEquals(95.0, segments.getFirst().endSeconds(), 0.001);
    }

    @Test
    @DisplayName("Deve remover múltiplos tipos de tags XML/HTML e entidades &amp; e &nbsp;")
    void shouldCleanXmlTagsAndEntities() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                <font color="#ffff00">Texto&nbsp;<b>em negrito</b></font> &amp; <00:00:02.000>palavra.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);
        assertEquals(1, segments.size());
        assertEquals("Texto em negrito & palavra.", segments.getFirst().text());
    }

    @Test
    @DisplayName("Deve retornar lista vazia se conteúdo for nulo ou em branco")
    void shouldReturnEmptyForBlankInput() {
        assertTrue(TranscriptParser.parseVtt(null).isEmpty());
        assertTrue(TranscriptParser.parseVtt("   ").isEmpty());
    }
}
