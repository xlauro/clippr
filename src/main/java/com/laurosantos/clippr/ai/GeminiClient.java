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
import java.util.concurrent.ThreadLocalRandom;

public class GeminiClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final String apiKey;
    private final String modelName;
    private final String fallbackModelName;
    private final HttpClient httpClient;
    private final int attemptsPerModel;
    private final int maxCycles;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final String baseUrl;

    public record AnalysisResult(List<ClipCandidate> approvedClips, List<ClipCandidate> discardedClips) {}

    public GeminiClient(String apiKey, String modelName) {
        this(apiKey, modelName, "gemini-3.7-flash", HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build(), 10, 5, 800L, 3500L, DEFAULT_BASE_URL);
    }

    public GeminiClient(String apiKey, String modelName, HttpClient httpClient) {
        this(apiKey, modelName, "gemini-3.7-flash", httpClient, 10, 5, 800L, 3500L, DEFAULT_BASE_URL);
    }

    public GeminiClient(String apiKey, String modelName, HttpClient httpClient, int attemptsPerModel, long initialBackoffMs, String baseUrl) {
        this(apiKey, modelName, "gemini-3.7-flash", httpClient, attemptsPerModel, 5, initialBackoffMs, 3500L, baseUrl);
    }

    public GeminiClient(
            String apiKey,
            String modelName,
            String fallbackModelName,
            HttpClient httpClient,
            int attemptsPerModel,
            int maxCycles,
            long initialBackoffMs,
            long maxBackoffMs,
            String baseUrl
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "gemini-3.8-flash";
        this.fallbackModelName = (fallbackModelName != null && !fallbackModelName.isBlank()) ? fallbackModelName.trim() : "gemini-3.7-flash";
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.attemptsPerModel = attemptsPerModel > 0 ? attemptsPerModel : 10;
        this.maxCycles = maxCycles > 0 ? maxCycles : 5;
        this.initialBackoffMs = initialBackoffMs > 0 ? initialBackoffMs : 800L;
        this.maxBackoffMs = maxBackoffMs > 0 ? maxBackoffMs : 3500L;
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

        List<String> models = new ArrayList<>();
        models.add(modelName);
        if (!fallbackModelName.equalsIgnoreCase(modelName)) {
            models.add(fallbackModelName);
        }

        HttpResponse<String> successfulResponse = null;
        String successfulModel = null;
        String lastErrorMsg = null;

        cycleLoop:
        for (int cycle = 1; cycle <= maxCycles; cycle++) {
            for (int mIndex = 0; mIndex < models.size(); mIndex++) {
                String currentModel = models.get(mIndex);
                String startMsg = "[Gemini] Conectando ao modelo %s (Tentativa %d de %d ciclos)...".formatted(currentModel, cycle, maxCycles);
                logger.info(startMsg);
                EventBus.getInstance().publish(new ClipprEvent.LogMessage(startMsg, false));

                String endpoint = "%s/%s:generateContent?key=%s".formatted(baseUrl, currentModel, apiKey);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofMinutes(2))
                        .build();

                for (int attempt = 1; attempt <= attemptsPerModel; attempt++) {
                    HttpResponse<String> response = null;
                    try {
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    } catch (java.io.IOException e) {
                        lastErrorMsg = e.getMessage();
                        if (attempt == attemptsPerModel) {
                            String connErr = "[Gemini %s] Falha de conexão na tentativa %d: %s".formatted(currentModel, attempt, e.getMessage());
                            logger.warn(connErr);
                            EventBus.getInstance().publish(new ClipprEvent.LogMessage(connErr, false));
                            break; // Esgotou tentativas neste modelo, avança para o próximo
                        }
                        long base = Math.min(maxBackoffMs, (long) (initialBackoffMs * Math.pow(1.3, attempt - 1)));
                        long jitter = ThreadLocalRandom.current().nextLong(100, 500);
                        long backoffMs = base + jitter;
                        String retryMsg = "[Gemini %s] Falha de conexão (%s). Tentativa %d de %d em %.1fs (Ciclo %d/%d)...".formatted(
                                currentModel, e.getMessage(), attempt + 1, attemptsPerModel, backoffMs / 1000.0, cycle, maxCycles);
                        logger.warn(retryMsg);
                        EventBus.getInstance().publish(new ClipprEvent.LogMessage(retryMsg, false));
                        EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated(0.4, "Reconectando %s (%d/%d em %.1fs)...".formatted(currentModel, attempt + 1, attemptsPerModel, backoffMs / 1000.0)));
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Processamento cancelado durante retry", ie);
                        }
                        continue;
                    }

                    int statusCode = response.statusCode();
                    if (statusCode == 200) {
                        successfulResponse = response;
                        successfulModel = currentModel;
                        String successMsg = "[Gemini %s] Resposta recebida com sucesso na tentativa %d do ciclo %d!".formatted(currentModel, attempt, cycle);
                        logger.info(successMsg);
                        EventBus.getInstance().publish(new ClipprEvent.LogMessage(successMsg, false));
                        break cycleLoop;
                    }

                    if (statusCode == 503 || statusCode == 429) {
                        String reason = statusCode == 503 ? "Alta demanda temporária (503)" : "Limite de requisições excedido (429)";
                        lastErrorMsg = reason;
                        if (attempt < attemptsPerModel) {
                            long base = Math.min(maxBackoffMs, (long) (initialBackoffMs * Math.pow(1.3, attempt - 1)));
                            long jitter = ThreadLocalRandom.current().nextLong(100, 500);
                            long backoffMs = base + jitter;
                            String retryMsg = "[Gemini %s] %s. Tentativa %d de %d em %.1fs (Ciclo %d/%d)...".formatted(
                                    currentModel, reason, attempt + 1, attemptsPerModel, backoffMs / 1000.0, cycle, maxCycles);
                            logger.warn(retryMsg);
                            EventBus.getInstance().publish(new ClipprEvent.LogMessage(retryMsg, false));
                            EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated(0.4, "%s ocupado (%d). Tentativa %d/%d em %.1fs...".formatted(currentModel, statusCode, attempt + 1, attemptsPerModel, backoffMs / 1000.0)));
                            try {
                                Thread.sleep(backoffMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Processamento cancelado durante retry", ie);
                            }
                        } else {
                            String nextModel = (mIndex + 1 < models.size()) ? models.get(mIndex + 1) : models.get(0);
                            int nextCycle = (mIndex + 1 < models.size()) ? cycle : cycle + 1;
                            String exhaustMsg = "[Gemini %s] %d tentativas esgotadas por %s. Alternando para o modelo %s (Ciclo %d/%d)...".formatted(
                                    currentModel, attemptsPerModel, reason, nextModel, Math.min(nextCycle, maxCycles), maxCycles);
                            logger.warn(exhaustMsg);
                            EventBus.getInstance().publish(new ClipprEvent.LogMessage(exhaustMsg, false));
                            EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated(0.4, "Alternando para %s...".formatted(nextModel)));
                            try {
                                Thread.sleep(500L);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Processamento cancelado durante alternância", ie);
                            }
                        }
                    } else {
                        String fatalError = "Erro na API Gemini (HTTP %d): %s".formatted(statusCode, response.body());
                        logger.error(fatalError);
                        EventBus.getInstance().publish(new ClipprEvent.LogMessage(fatalError, true));
                        throw new RuntimeException(fatalError);
                    }
                }
            }
        }

        if (successfulResponse == null) {
            String exhaustedAll = "Todos os modelos testados (%s) apresentaram sobrecarga persistente após %d ciclos de %d tentativas. Último erro: %s. Tente novamente em instantes.".formatted(
                    String.join(", ", models), maxCycles, attemptsPerModel, lastErrorMsg);
            logger.error(exhaustedAll);
            EventBus.getInstance().publish(new ClipprEvent.LogMessage(exhaustedAll, true));
            throw new RuntimeException(exhaustedAll);
        }

        List<ClipCandidate> rawCandidates = parseGeminiResponse(videoSourceId, successfulResponse.body());
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
                        ProcessingStatus.DISCARDED_LOW_SCORE,
                        candidate.thumbnailTimestamp(),
                        candidate.thumbnailHeadline(),
                        candidate.thumbnailPath(),
                        candidate.rawFramePath()
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

            Double thumbnailTimestamp = null;
            if (item.hasNonNull("thumbnailTimestamp")) {
                double rawTs = item.path("thumbnailTimestamp").asDouble(startSeconds);
                if (rawTs >= startSeconds && rawTs <= endSeconds) {
                    thumbnailTimestamp = rawTs;
                } else {
                    thumbnailTimestamp = startSeconds;
                }
            } else {
                thumbnailTimestamp = startSeconds;
            }

            String thumbnailHeadline = null;
            if (item.hasNonNull("thumbnailHeadline")) {
                String hl = item.path("thumbnailHeadline").asText().trim();
                if (!hl.isBlank()) {
                    thumbnailHeadline = hl;
                }
            }
            if (thumbnailHeadline == null) {
                thumbnailHeadline = title;
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
                        ProcessingStatus.PENDING_APPROVAL,
                        thumbnailTimestamp,
                        thumbnailHeadline,
                        null,
                        null
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

            Double thumbTs = c.thumbnailTimestamp();
            if (thumbTs != null) {
                if (thumbTs < start || thumbTs > end) {
                    thumbTs = start;
                }
            } else {
                thumbTs = start;
            }

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
                    c.status(),
                    thumbTs,
                    c.thumbnailHeadline(),
                    c.thumbnailPath(),
                    c.rawFramePath()
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
