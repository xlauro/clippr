package com.laurosantos.clippr.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GeminiClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final String apiKey;
    private final String modelName;
    private final HttpClient httpClient;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final String baseUrl;

    public record AnalysisResult(List<ClipCandidate> approvedClips, List<ClipCandidate> discardedClips) {}

    public GeminiClient(String apiKey, String modelName) {
        this(apiKey, modelName, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build(), 5, 2000L, DEFAULT_BASE_URL);
    }

    public GeminiClient(String apiKey, String modelName, HttpClient httpClient) {
        this(apiKey, modelName, httpClient, 5, 2000L, DEFAULT_BASE_URL);
    }

    public GeminiClient(String apiKey, String modelName, HttpClient httpClient, int maxRetries, long initialBackoffMs) {
        this(apiKey, modelName, httpClient, maxRetries, initialBackoffMs, DEFAULT_BASE_URL);
    }

    public GeminiClient(String apiKey, String modelName, HttpClient httpClient, int maxRetries, long initialBackoffMs, String baseUrl) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "gemini-3.8-flash";
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.maxRetries = maxRetries > 0 ? maxRetries : 1;
        this.initialBackoffMs = initialBackoffMs > 0 ? initialBackoffMs : 500L;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
    }

    public AnalysisResult analyzeTranscript(
            Long videoSourceId,
            String videoTitle,
            String videoAuthor,
            List<TranscriptSegment> segments,
            int minViralityThreshold
    ) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Chave da API do Gemini não configurada. Configure na tela de configurações ou via GEMINI_API_KEY.");
        }

        if (segments == null || segments.isEmpty()) {
            logger.warn("Nenhum segmento de transcrição para analisar.");
            return new AnalysisResult(List.of(), List.of());
        }

        String formattedTranscript = com.laurosantos.clippr.downloader.TranscriptParser.formatForAi(segments);
        String userPrompt = PromptFactory.buildUserPrompt(videoTitle, videoAuthor, formattedTranscript);

        logger.info("Enviando transcrição para avaliação do Gemini ({})", modelName);
        EventBus.getInstance().publish(new ClipprEvent.LogMessage("Enviando transcrição para análise de viralização via Gemini...", false));

        String requestBody = buildRequestBody(userPrompt);
        String endpoint = "%s/%s:generateContent?key=%s".formatted(baseUrl, modelName, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(2))
                .build();

        HttpResponse<String> response = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException e) {
                if (attempt == maxRetries) {
                    String netErr = "Falha de conexão ao comunicar com a API Gemini após %d tentativas: %s".formatted(maxRetries, e.getMessage());
                    logger.error(netErr, e);
                    EventBus.getInstance().publish(new ClipprEvent.LogMessage(netErr, false));
                    throw new RuntimeException(netErr, e);
                }
                long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(500, 1500);
                long backoffMs = (long) (initialBackoffMs * Math.pow(2, attempt - 1)) + jitter;
                String retryMsg = "[Gemini %s] Falha de conexão (%s). Tentativa %d de %d em %.1fs...".formatted(
                        modelName, e.getMessage(), attempt + 1, maxRetries, backoffMs / 1000.0);
                logger.warn(retryMsg);
                EventBus.getInstance().publish(new ClipprEvent.LogMessage(retryMsg, false));
                EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated(0.4, "Reconectando Gemini (%d/%d em %.0fs)...".formatted(attempt + 1, maxRetries, backoffMs / 1000.0)));
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Processamento cancelado durante retry do Gemini", ie);
                }
                continue;
            }

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                if (attempt > 1) {
                    String successMsg = "[Gemini %s] Resposta recebida com sucesso na tentativa %d!".formatted(modelName, attempt);
                    logger.info(successMsg);
                    EventBus.getInstance().publish(new ClipprEvent.LogMessage(successMsg, false));
                }
                break;
            }

            if ((statusCode == 503 || statusCode == 429) && attempt < maxRetries) {
                String reason = statusCode == 503 ? "Alta demanda temporária (HTTP 503)" : "Limite de requisições excedido (HTTP 429)";
                long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(500, 1500);
                long backoffMs = (long) (initialBackoffMs * Math.pow(2, attempt - 1)) + jitter;
                String retryMsg = "[Gemini %s] %s. Aguardando %.1fs antes da tentativa %d de %d...".formatted(
                        modelName, reason, backoffMs / 1000.0, attempt + 1, maxRetries);
                logger.warn(retryMsg);
                EventBus.getInstance().publish(new ClipprEvent.LogMessage(retryMsg, false));
                EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated(0.4, "Gemini ocupado (%d). Nova tentativa %d/%d em %.0fs...".formatted(statusCode, attempt + 1, maxRetries, backoffMs / 1000.0)));

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Processamento cancelado durante retry do Gemini", ie);
                }
                continue;
            }

            String errorMsg;
            if (statusCode == 503) {
                errorMsg = "O modelo %s está temporariamente com alta demanda no Google (HTTP 503 após %d tentativas). Tente novamente em alguns instantes.".formatted(modelName, maxRetries);
            } else if (statusCode == 429) {
                errorMsg = "Limite de taxa (HTTP 429) excedido na API Gemini após %d tentativas. Tente novamente em alguns instantes.".formatted(maxRetries);
            } else {
                errorMsg = "Erro na API Gemini (HTTP %d): %s".formatted(statusCode, response.body());
            }

            logger.error(errorMsg);
            EventBus.getInstance().publish(new ClipprEvent.LogMessage(errorMsg, true));
            throw new RuntimeException(errorMsg);
        }

        List<ClipCandidate> rawCandidates = parseGeminiResponse(videoSourceId, response.body());
        List<ClipCandidate> snappedCandidates = snapToSegmentBoundaries(rawCandidates, segments);

        List<ClipCandidate> approved = new ArrayList<>();
        List<ClipCandidate> discarded = new ArrayList<>();

        for (ClipCandidate candidate : snappedCandidates) {
            if (candidate.viralityScore() >= minViralityThreshold) {
                approved.add(candidate);
                logger.info("Corte aprovado (Score {}): \"{}\" [{}s - {}s]",
                        candidate.viralityScore(), candidate.title(), candidate.startSeconds(), candidate.endSeconds());
            } else {
                discarded.add(new ClipCandidate(
                        candidate.id(),
                        candidate.videoSourceId(),
                        candidate.title(),
                        candidate.hook(),
                        candidate.startSeconds(),
                        candidate.endSeconds(),
                        candidate.viralityScore(),
                        candidate.reasoning(),
                        candidate.suggestedCaption(),
                        candidate.hashtags(),
                        candidate.orientation(),
                        candidate.hasSubtitles(),
                        candidate.outputFilePath(),
                        candidate.metadataJsonPath(),
                        ProcessingStatus.DISCARDED_LOW_SCORE
                ));
                logger.info("Corte descartado (Score {} < {}): \"{}\"",
                        candidate.viralityScore(), minViralityThreshold, candidate.title());
            }
        }

        EventBus.getInstance().publish(new ClipprEvent.LogMessage(
                "Análise concluída: %d corte(s) qualificado(s) (nota >= %d), %d descartado(s)."
                        .formatted(approved.size(), minViralityThreshold, discarded.size()),
                false
        ));

        return new AnalysisResult(approved, discarded);
    }

    private String buildRequestBody(String userPrompt) {
        ObjectNode root = MAPPER.createObjectNode();

        // System Instruction
        ObjectNode systemInstruction = root.putObject("system_instruction");
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", PromptFactory.SYSTEM_INSTRUCTION);

        // Contents
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", userPrompt);

        // Generation Config
        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("response_mime_type", "application/json");
        genConfig.put("temperature", 0.2);

        return root.toString();
    }

    public List<ClipCandidate> parseGeminiResponse(Long videoSourceId, String jsonResponse) throws Exception {
        JsonNode root = MAPPER.readTree(jsonResponse);
        JsonNode candidatesNode = root.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            throw new IllegalStateException("Resposta da IA não contém candidatos válidos: " + jsonResponse);
        }

        String textContent = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText("");
        if (textContent.isBlank()) {
            return List.of();
        }

        JsonNode parsedJson = MAPPER.readTree(textContent);
        JsonNode clipsArray = parsedJson.isArray() ? parsedJson :
                parsedJson.has("clips") ? parsedJson.path("clips") :
                parsedJson.has("candidates") ? parsedJson.path("candidates") : null;

        if (clipsArray == null || !clipsArray.isArray()) {
            logger.warn("Estrutura JSON não esperada da IA: {}", textContent);
            return List.of();
        }

        List<ClipCandidate> list = new ArrayList<>();
        for (JsonNode item : clipsArray) {
            String title = item.path("title").asText("Corte Viral");
            String hook = item.path("hook").asText("");
            double startSeconds = item.path("startSeconds").asDouble(0.0);
            double endSeconds = item.path("endSeconds").asDouble(0.0);
            int score = item.path("viralityScore").asInt(item.path("score").asInt(50));
            String reasoning = item.path("reasoning").asText("");
            String caption = item.path("suggestedCaption").asText(title);

            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = item.path("hashtags");
            if (tagsNode.isArray()) {
                for (JsonNode t : tagsNode) tags.add(t.asText());
            }

            if (endSeconds > startSeconds) {
                list.add(new ClipCandidate(
                        null,
                        videoSourceId,
                        title,
                        hook,
                        startSeconds,
                        endSeconds,
                        score,
                        reasoning,
                        caption,
                        tags,
                        OrientationMode.AUTO,
                        true,
                        null,
                        null,
                        ProcessingStatus.PENDING_APPROVAL
                ));
            }
        }

        return list;
    }

    public List<ClipCandidate> snapToSegmentBoundaries(List<ClipCandidate> candidates, List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) return candidates;

        List<ClipCandidate> snappedList = new ArrayList<>();
        for (ClipCandidate c : candidates) {
            double start = findClosestBoundary(c.startSeconds(), segments, true);
            double end = findClosestBoundary(c.endSeconds(), segments, false);
            if (end <= start) end = start + c.durationSeconds();

            snappedList.add(new ClipCandidate(
                    c.id(),
                    c.videoSourceId(),
                    c.title(),
                    c.hook(),
                    start,
                    end,
                    c.viralityScore(),
                    c.reasoning(),
                    c.suggestedCaption(),
                    c.hashtags(),
                    c.orientation(),
                    c.hasSubtitles(),
                    c.outputFilePath(),
                    c.metadataJsonPath(),
                    c.status()
            ));
        }
        return snappedList;
    }

    private double findClosestBoundary(double targetSeconds, List<TranscriptSegment> segments, boolean isStart) {
        double closest = targetSeconds;
        double minDiff = Double.MAX_VALUE;

        for (TranscriptSegment seg : segments) {
            double candidate = isStart ? seg.startSeconds() : seg.endSeconds();
            double diff = Math.abs(candidate - targetSeconds);
            if (diff < minDiff && diff <= 3.0) { // Tolera até 3 segundos de ajuste para borda limpa
                minDiff = diff;
                closest = candidate;
            }
        }
        return closest;
    }
}
