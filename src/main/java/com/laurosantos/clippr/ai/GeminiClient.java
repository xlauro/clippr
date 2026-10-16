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

    public record AnalysisResult(List<ClipCandidate> approvedClips, List<ClipCandidate> discardedClips) {}

    public GeminiClient(String apiKey, String modelName) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "gemini-2.5-flash";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
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
        String endpoint = "%s/%s:generateContent?key=%s".formatted(DEFAULT_BASE_URL, modelName, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(2))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = "Erro na API Gemini (HTTP %d): %s".formatted(response.statusCode(), response.body());
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

        EventBus.getInstance().publish(new ClipprEvent.ClipsEvaluated(videoSourceId, approved, discarded.size()));

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
