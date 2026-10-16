package com.laurosantos.clippr.downloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptParserComprehensiveTest {

    @Test
    @DisplayName("Deve analisar formato SRT com vírgula nos milissegundos e números de sequência")
    void shouldParseSrtFormatWithCommaAndSequenceNumbers() {
        String srt = """
                1
                00:00:01,500 --> 00:00:04,200
                Primeira linha em formato SRT.
                
                2
                00:00:04,200 --> 00:00:07,800
                Segunda linha em formato SRT.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(srt);

        assertEquals(2, segments.size());
        assertEquals("Primeira linha em formato SRT.", segments.get(0).text());
        assertEquals(1.5, segments.get(0).startSeconds(), 0.001);
        assertEquals(4.2, segments.get(0).endSeconds(), 0.001);

        assertEquals("Segunda linha em formato SRT.", segments.get(1).text());
        assertEquals(4.2, segments.get(1).startSeconds(), 0.001);
        assertEquals(7.8, segments.get(1).endSeconds(), 0.001);
    }

    @Test
    @DisplayName("Deve mesclar legendas rolantes progressivas típicas do YouTube")
    void shouldMergeProgressiveScrollingCaptions() {
        String scrollingVtt = """
                WEBVTT
                Kind: captions
                Language: pt
                
                00:00:00.080 --> 00:00:01.350
                Isso aqui que tá
                
                00:00:01.350 --> 00:00:01.360
                Isso aqui que tá
                
                00:00:01.360 --> 00:00:03.310
                Isso aqui que tá acontecendo é uma loucura.
                
                00:00:03.310 --> 00:00:06.000
                Um único desenvolvedor tá tentando desafiar empresas.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(scrollingVtt);

        assertEquals(2, segments.size());
        assertEquals("Isso aqui que tá acontecendo é uma loucura.", segments.get(0).text());
        assertEquals(0.08, segments.get(0).startSeconds(), 0.001);
        assertEquals(3.31, segments.get(0).endSeconds(), 0.001);

        assertEquals("Um único desenvolvedor tá tentando desafiar empresas.", segments.get(1).text());
        assertEquals(3.31, segments.get(1).startSeconds(), 0.001);
        assertEquals(6.0, segments.get(1).endSeconds(), 0.001);
    }

    @Test
    @DisplayName("Deve limpar tags de karaoke/palavra por palavra do YouTube e tags de estilo")
    void shouldCleanKaraokeAndStyleTags() {
        String vtt = """
                WEBVTT
                
                00:00:00.100 --> 00:00:02.500 align:start position:0%
                Isso<00:00:00.200><c> aqui</c><00:00:00.320><c> é</c> <c.colorFFFF00>incrível</c>!
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);

        assertEquals(1, segments.size());
        assertEquals("Isso aqui é incrível!", segments.getFirst().text());
    }

    @Test
    @DisplayName("Deve decodificar entidades HTML comuns em legendas")
    void shouldDecodeHtmlEntities() {
        String vtt = """
                WEBVTT
                
                00:00:01.000 --> 00:00:03.000
                Tom&nbsp;&amp;&nbsp;Jerry &gt; outros &lt;desenhos&gt;
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);

        assertEquals(1, segments.size());
        // A regex remove tags <desenhos>, preservando o restante limpo
        assertTrue(segments.getFirst().text().contains("Tom & Jerry"));
    }

    @Test
    @DisplayName("Deve agrupar múltiplas linhas de texto dentro do mesmo bloco de tempo")
    void shouldGroupMultiLineTextWithinSameCue() {
        String vtt = """
                WEBVTT
                
                00:00:02.000 --> 00:00:05.000
                Primeira linha da mesma fala.
                Segunda linha que complementa a ideia.
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);

        assertEquals(1, segments.size());
        assertEquals("Primeira linha da mesma fala. Segunda linha que complementa a ideia.", segments.getFirst().text());
    }

    @Test
    @DisplayName("Deve formatar corretamente a transcrição para consumo pela IA Gemini")
    void shouldFormatForAiPromptCorrectly() {
        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment(0.5, 3.2, "Olá a todos."),
                new TranscriptSegment(3.2, 7.8, "Hoje vamos falar sobre IA.")
        );

        String formatted = TranscriptParser.formatForAi(segments);

        assertNotNull(formatted);
        assertTrue(formatted.contains("[00:00:00.500 -> 00:00:03.200] Olá a todos."));
        assertTrue(formatted.contains("[00:00:03.200 -> 00:00:07.800] Hoje vamos falar sobre IA."));
    }

    @Test
    @DisplayName("Deve ignorar blocos vazios ou que contenham apenas comentários WEBVTT")
    void shouldHandleEmptyOrCommentsOnlyVtt() {
        String vtt = """
                WEBVTT
                
                NOTE Comentário do autor
                NOTE Outro comentário explicativo
                
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);
        assertTrue(segments.isEmpty());
    }
}
