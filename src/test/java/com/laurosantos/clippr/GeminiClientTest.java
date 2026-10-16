package com.laurosantos.clippr;

import com.laurosantos.clippr.ai.GeminiClient;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientTest {

    @Test
    @DisplayName("Deve fazer parse do JSON retornado pela API Gemini e extrair candidatos estruturados")
    void shouldParseGeminiResponseCorrectly() throws Exception {
        GeminiClient client = new GeminiClient("fake-key", "gemini-2.5-flash");

        String simulatedApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "[{\\"title\\":\\"O Erro que me custou 1 milhão\\",\\"hook\\":\\"Eu perdi tudo em 24 horas...\\",\\"startSeconds\\":10.2,\\"endSeconds\\":45.8,\\"viralityScore\\":94,\\"reasoning\\":\\"História dramática com início, meio e fim perfeitos.\\",\\"suggestedCaption\\":\\"Nunca faça isso com seu dinheiro! #financas\\",\\"hashtags\\":[\\"#viral\\",\\"#dinheiro\\",\\"#shorts\\"]},{\\"title\\":\\"Conversa fiada sobre o tempo\\",\\"hook\\":\\"Hoje está chovendo lá fora\\",\\"startSeconds\\":60.0,\\"endSeconds\\":90.0,\\"viralityScore\\":65,\\"reasoning\\":\\"Assunto morno sem apelo emocional.\\",\\"suggestedCaption\\":\\"Dia chuvoso\\",\\"hashtags\\":[\\"#clima\\"]}]"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        List<ClipCandidate> candidates = client.parseGeminiResponse(1L, simulatedApiResponse);

        assertEquals(2, candidates.size());

        ClipCandidate viral = candidates.get(0);
        assertEquals("O Erro que me custou 1 milhão", viral.title());
        assertEquals("Eu perdi tudo em 24 horas...", viral.hook());
        assertEquals(94, viral.viralityScore());
        assertEquals(3, viral.hashtags().size());

        ClipCandidate lowScore = candidates.get(1);
        assertEquals(65, lowScore.viralityScore());
    }

    @Test
    @DisplayName("Deve ajustar bordas de tempo (snap) com base nos segmentos reais da transcrição")
    void shouldSnapTimestampsToActualSegments() {
        GeminiClient client = new GeminiClient("fake-key", "gemini-2.5-flash");

        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment(10.0, 15.0, "Eu perdi tudo"),
                new TranscriptSegment(15.0, 30.0, "porque cometi o maior erro"),
                new TranscriptSegment(30.0, 46.0, "da minha vida inteira.")
        );

        ClipCandidate candidate = new ClipCandidate(
                null, 1L, "Título", "Gancho",
                10.8, // Gemini deu 10.8s, mas a fala começou em 10.0s
                45.2, // Gemini deu 45.2s, mas a frase terminou em 46.0s
                95, "Motivo", "Legenda", List.of(), null, true, null, null, null
        );

        List<ClipCandidate> snapped = client.snapToSegmentBoundaries(List.of(candidate), segments);

        assertEquals(1, snapped.size());
        assertEquals(10.0, snapped.getFirst().startSeconds(), 0.001, "O início deve fazer snap para 10.0s");
        assertEquals(46.0, snapped.getFirst().endSeconds(), 0.001, "O término deve fazer snap para 46.0s");
    }
}
