package com.laurosantos.clippr;

import com.laurosantos.clippr.downloader.TranscriptParser;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptParserTest {

    @Test
    @DisplayName("Deve analisar arquivo WebVTT e extrair segmentos com timestamps precisos em segundos")
    void shouldParseVttContentCorrectly() {
        String vtt = """
                WEBVTT
                Kind: captions
                Language: pt

                00:00:01.200 --> 00:00:04.500
                <c.colorE5E5E5>Olá pessoal, hoje vou revelar</c>

                00:00:04.500 --> 00:00:08.150
                o maior segredo da inteligência artificial.

                00:00:08.150 --> 00:00:12.000
                Prestem muita atenção nisso!
                """;

        List<TranscriptSegment> segments = TranscriptParser.parseVtt(vtt);

        assertEquals(3, segments.size());

        TranscriptSegment first = segments.get(0);
        assertEquals(1.2, first.startSeconds(), 0.001);
        assertEquals(4.5, first.endSeconds(), 0.001);
        assertEquals("Olá pessoal, hoje vou revelar", first.text());

        TranscriptSegment second = segments.get(1);
        assertEquals(4.5, second.startSeconds(), 0.001);
        assertEquals(8.15, second.endSeconds(), 0.001);
        assertEquals("o maior segredo da inteligência artificial.", second.text());

        String formatted = TranscriptParser.formatForAi(segments);
        assertTrue(formatted.contains("[00:00:01.200 -> 00:00:04.500]"));
        assertTrue(formatted.contains("Prestem muita atenção nisso!"));
    }
}
