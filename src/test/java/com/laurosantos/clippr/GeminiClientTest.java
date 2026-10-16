package com.laurosantos.clippr;

import com.laurosantos.clippr.ai.GeminiClient;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientTest {

    @Test
    @DisplayName("Deve fazer parse do JSON retornado pela API Gemini e extrair candidatos estruturados")
    void shouldParseGeminiResponseCorrectly() throws Exception {
        GeminiClient client = new GeminiClient("fake-key", "gemini-3.8-flash");

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
    @DisplayName("Deve fazer parse de cortes longos e curtos com sobreposição (overlapping/nesting)")
    void shouldParseLongAndShortOverlappingClipsFromGeminiResponse() throws Exception {
        GeminiClient client = new GeminiClient("fake-key", "gemini-3.8-flash");

        String simulatedApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "[{\\"title\\":\\"Debate Completo: O Futuro da Inteligência Artificial\\",\\"hook\\":\\"A inteligência artificial vai substituir programadores?\\",\\"startSeconds\\":120.0,\\"endSeconds\\":600.0,\\"viralityScore\\":93,\\"reasoning\\":\\"Discussão densa e aprofundada com arco narrativo completo e alta retenção.\\",\\"suggestedCaption\\":\\"Debate completo sobre o impacto da IA #tech #ia\\",\\"hashtags\\":[\\"#ia\\",\\"#futuro\\",\\"#tecnologia\\"]},{\\"title\\":\\"A Revelação Chocante sobre IA\\",\\"hook\\":\\"Em 3 anos ninguém mais programa do jeito atual...\\",\\"startSeconds\\":180.0,\\"endSeconds\\":240.0,\\"viralityScore\\":98,\\"reasoning\\":\\"Pílula viral de impacto imediato extraída do debate longo com gancho fortíssimo nos primeiros 3s.\\",\\"suggestedCaption\\":\\"Você concorda com isso? #shorts #ia\\",\\"hashtags\\":[\\"#viral\\",\\"#shorts\\",\\"#dev\\"]}]"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        List<ClipCandidate> candidates = client.parseGeminiResponse(1L, simulatedApiResponse);

        assertEquals(2, candidates.size());

        ClipCandidate longClip = candidates.get(0);
        assertEquals("Debate Completo: O Futuro da Inteligência Artificial", longClip.title());
        assertEquals(480.0, longClip.durationSeconds(), 0.001);
        assertTrue(longClip.isLongClip());
        assertEquals("🎬 Longo", longClip.clipTypeBadge());
        assertEquals("08m 00s", longClip.formattedDuration());
        assertEquals("02:00 - 10:00 (08m 00s)", longClip.formattedDisplayInterval());

        ClipCandidate shortClip = candidates.get(1);
        assertEquals("A Revelação Chocante sobre IA", shortClip.title());
        assertEquals(60.0, shortClip.durationSeconds(), 0.001);
        assertFalse(shortClip.isLongClip());
        assertEquals("⚡ Curto", shortClip.clipTypeBadge());
        assertEquals("01m 00s", shortClip.formattedDuration());
        assertEquals("03:00 - 04:00 (01m 00s)", shortClip.formattedDisplayInterval());

        // Validação da sobreposição (o corte curto está contido dentro do corte longo)
        assertTrue(shortClip.startSeconds() >= longClip.startSeconds());
        assertTrue(shortClip.endSeconds() <= longClip.endSeconds());
    }

    @Test
    @DisplayName("Deve ajustar bordas de tempo (snap) com base nos segmentos reais da transcrição")
    void shouldSnapTimestampsToActualSegments() {
        GeminiClient client = new GeminiClient("fake-key", "gemini-3.8-flash");

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

    @Test
    @DisplayName("Deve fazer parse de thumbnailTimestamp e thumbnailHeadline com fallbacks adequados")
    void shouldParseThumbnailTimestampAndHeadlineFromGeminiResponse() throws Exception {
        GeminiClient client = new GeminiClient("fake-key", "gemini-3.8-flash");

        String simulatedApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "[{\\"title\\":\\"Corte com Capa Completa\\",\\"hook\\":\\"Gancho\\",\\"startSeconds\\":100.0,\\"endSeconds\\":500.0,\\"viralityScore\\":96,\\"reasoning\\":\\"Arco completo\\",\\"suggestedCaption\\":\\"Legenda\\",\\"hashtags\\":[],\\"thumbnailTimestamp\\":250.5,\\"thumbnailHeadline\\":\\"O SEGREDO REVELADO\\"},{\\"title\\":\\"Corte Sem Capa Explícita\\",\\"hook\\":\\"Gancho 2\\",\\"startSeconds\\":50.0,\\"endSeconds\\":80.0,\\"viralityScore\\":91,\\"reasoning\\":\\"Bom\\",\\"suggestedCaption\\":\\"Legenda 2\\",\\"hashtags\\":[]},{\\"title\\":\\"Corte com Timestamp Fora dos Limites\\",\\"hook\\":\\"Gancho 3\\",\\"startSeconds\\":20.0,\\"endSeconds\\":40.0,\\"viralityScore\\":90,\\"reasoning\\":\\"Bom 3\\",\\"suggestedCaption\\":\\"Legenda 3\\",\\"hashtags\\":[],\\"thumbnailTimestamp\\":999.0,\\"thumbnailHeadline\\":\\"   \\"}]"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        List<ClipCandidate> candidates = client.parseGeminiResponse(1L, simulatedApiResponse);
        assertEquals(3, candidates.size());

        // 1. Completo
        ClipCandidate complete = candidates.get(0);
        assertEquals(250.5, complete.thumbnailTimestamp());
        assertEquals("O SEGREDO REVELADO", complete.thumbnailHeadline());

        // 2. Sem campos -> fallback para startSeconds e title
        ClipCandidate fallback = candidates.get(1);
        assertEquals(50.0, fallback.thumbnailTimestamp());
        assertEquals("Corte Sem Capa Explícita", fallback.thumbnailHeadline());

        // 3. Fora dos limites e headline em branco -> fallback para startSeconds e title
        ClipCandidate invalid = candidates.get(2);
        assertEquals(20.0, invalid.thumbnailTimestamp());
        assertEquals("Corte com Timestamp Fora dos Limites", invalid.thumbnailHeadline());

        // Snap to segment boundaries deve preservar thumbnailTimestamp e thumbnailHeadline
        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment(98.0, 150.0, "Fala 1"),
                new TranscriptSegment(150.0, 502.0, "Fala 2")
        );
        List<ClipCandidate> snapped = client.snapToSegmentBoundaries(List.of(complete), segments);
        assertEquals(1, snapped.size());
        assertEquals(250.5, snapped.getFirst().thumbnailTimestamp());
        assertEquals("O SEGREDO REVELADO", snapped.getFirst().thumbnailHeadline());
    }

    @Test
    @DisplayName("Deve re-tentar automaticamente quando receber HTTP 503 e obter sucesso na tentativa seguinte")
    void shouldRetryAndRecoverOnHttp503() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                String error503 = """
                        {
                          "error": {
                            "code": 503,
                            "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
                            "status": "UNAVAILABLE"
                          }
                        }
                        """;
                exchange.sendResponseHeaders(503, error503.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error503.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                String success200 = """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "[{\\"title\\":\\"Vídeo Viral Teste\\",\\"hook\\":\\"Gancho\\",\\"startSeconds\\":10.0,\\"endSeconds\\":30.0,\\"viralityScore\\":95,\\"reasoning\\":\\"Bom\\",\\"suggestedCaption\\":\\"Cap\\",\\"hashtags\\":[]}]"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """;
                exchange.sendResponseHeaders(200, success200.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(success200.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiClient client = new GeminiClient("test-key", "gemini-3.8-flash", null, 3, 10L, baseUrl);
            var result = client.analyzeTranscript(
                    1L,
                    "Teste",
                    "Autor",
                    List.of(new TranscriptSegment(10.0, 30.0, "Texto")),
                    90
            );

            assertEquals(2, callCount.get(), "Deve ter feito exatamente 2 chamadas (1 falha 503 + 1 sucesso)");
            assertEquals(1, result.approvedClips().size());
            assertEquals("Vídeo Viral Teste", result.approvedClips().getFirst().title());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Deve esgotar retries e lançar RuntimeException após persistência do erro HTTP 503")
    void shouldThrowExceptionWhenHttp503Persists() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            callCount.incrementAndGet();
            String error503 = """
                    {
                      "error": {
                        "code": 503,
                        "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
                        "status": "UNAVAILABLE"
                      }
                    }
                    """;
            exchange.sendResponseHeaders(503, error503.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error503.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiClient client = new GeminiClient("test-key", "gemini-3.8-flash", "gemini-3.8-flash", null, 3, 1, 10L, 50L, baseUrl);
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    client.analyzeTranscript(
                            1L,
                            "Teste",
                            "Autor",
                            List.of(new TranscriptSegment(10.0, 30.0, "Texto")),
                            90
                    )
            );

            assertEquals(3, callCount.get(), "Deve ter realizado as 3 tentativas antes de falhar");
            assertTrue(ex.getMessage().contains("sobrecarga") || ex.getMessage().contains("503"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Deve alternar para o modelo secundário de fallback após esgotar tentativas do modelo primário")
    void shouldFallbackToSecondaryModelWhenPrimaryModelFails() throws Exception {
        java.util.List<String> requestedUris = new java.util.concurrent.CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String uri = exchange.getRequestURI().toString();
            requestedUris.add(uri);
            if (uri.contains("gemini-3.8-flash")) {
                String error503 = """
                        {
                          "error": {
                            "code": 503,
                            "message": "This model is currently experiencing high demand.",
                            "status": "UNAVAILABLE"
                          }
                        }
                        """;
                exchange.sendResponseHeaders(503, error503.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error503.getBytes(StandardCharsets.UTF_8));
                }
            } else if (uri.contains("gemini-3.7-flash")) {
                String success200 = """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "[{\\"title\\":\\"Fallback Sucesso\\",\\"hook\\":\\"Gancho Fallback\\",\\"startSeconds\\":10.0,\\"endSeconds\\":30.0,\\"viralityScore\\":95,\\"reasoning\\":\\"Bom\\",\\"suggestedCaption\\":\\"Cap\\",\\"hashtags\\":[]}]"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """;
                exchange.sendResponseHeaders(200, success200.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(success200.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiClient client = new GeminiClient("test-key", "gemini-3.8-flash", "gemini-3.7-flash", null, 2, 1, 5L, 20L, baseUrl);
            var result = client.analyzeTranscript(
                    1L,
                    "Teste",
                    "Autor",
                    List.of(new TranscriptSegment(10.0, 30.0, "Texto")),
                    90
            );

            assertEquals(3, requestedUris.size(), "Deve ter feito 2 requisições ao 3.8-flash e 1 ao 3.7-flash");
            assertTrue(requestedUris.get(0).contains("gemini-3.8-flash"));
            assertTrue(requestedUris.get(1).contains("gemini-3.8-flash"));
            assertTrue(requestedUris.get(2).contains("gemini-3.7-flash"));
            assertEquals(1, result.approvedClips().size());
            assertEquals("Fallback Sucesso", result.approvedClips().getFirst().title());
        } finally {
            server.stop(0);
        }
    }
}
