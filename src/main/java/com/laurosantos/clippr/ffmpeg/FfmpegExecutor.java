package com.laurosantos.clippr.ffmpeg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.domain.VideoSource;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FfmpegExecutor {

    private static final Logger logger = LoggerFactory.getLogger(FfmpegExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2}\\.\\d+)");

    private final String ffmpegBinary;

    public FfmpegExecutor() {
        this.ffmpegBinary = resolveFfmpegBinary();
    }

    public FfmpegExecutor(String customFfmpegBinary) {
        this.ffmpegBinary = customFfmpegBinary;
    }

    public String getFfmpegBinary() {
        return ffmpegBinary;
    }

    private String resolveFfmpegBinary() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, "ffmpeg");
                if (Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            }
        }
        if (Files.isExecutable(Path.of("/usr/bin/ffmpeg"))) {
            return "/usr/bin/ffmpeg";
        }
        return "ffmpeg";
    }

    public ClipCandidate renderClip(
            VideoSource source,
            ClipCandidate clip,
            Path sourceVideoFile,
            Path outputDirectory,
            List<TranscriptSegment> segments,
            int cpuThreads,
            boolean useBlurredBackground
    ) throws Exception {
        Files.createDirectories(outputDirectory);

        double duration = clip.durationSeconds();
        String safeTitle = clip.title().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safeTitle.length() > 50) safeTitle = safeTitle.substring(0, 50);

        String outputFileName = "clip_%s_%03d_%s.mp4".formatted(source.videoId(), clip.viralityScore(), safeTitle);
        Path outputFile = outputDirectory.resolve(outputFileName);
        Path jsonMetadataFile = outputDirectory.resolve(outputFileName.replace(".mp4", ".json"));

        EventBus.getInstance().publish(new ClipprEvent.LogMessage(
                "Iniciando renderização FFmpeg (100%% CPU): %s (%.1fs - %.1fs)"
                        .formatted(clip.title(), clip.startSeconds(), clip.endSeconds()),
                false
        ));

        OrientationMode orientation = clip.orientation() != null ? clip.orientation() : OrientationMode.VERTICAL_9_16;

        // Subtítulos .ass opcionais
        Path assFile = null;
        if (clip.hasSubtitles() && segments != null && !segments.isEmpty()) {
            Path tempSubDir = Files.createDirectories(Path.of("temp", "subtitles"));
            assFile = tempSubDir.resolve("sub_%d.ass".formatted(System.currentTimeMillis()));
            SubtitleGenerator.generateAssSubtitles(segments, clip.startSeconds(), clip.endSeconds(), orientation, assFile);
        }

        List<String> command = buildFfmpegCommand(
                sourceVideoFile,
                outputFile,
                clip.startSeconds(),
                duration,
                orientation,
                assFile,
                cpuThreads,
                useBlurredBackground
        );

        logger.info("Executando comando FFmpeg: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                EventBus.getInstance().publish(new ClipprEvent.LogMessage("[FFmpeg] " + line, true));
                Matcher matcher = TIME_PATTERN.matcher(line);
                if (matcher.find()) {
                    double hours = Double.parseDouble(matcher.group(1));
                    double mins = Double.parseDouble(matcher.group(2));
                    double secs = Double.parseDouble(matcher.group(3));
                    double currentSeconds = (hours * 3600) + (mins * 60) + secs;
                    double progress = Math.min(1.0, currentSeconds / Math.max(1.0, duration));
                    EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated("CUTTING_FFMPEG", progress, line));
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Falha na execução do FFmpeg para o corte (exit code: " + exitCode + ")");
        }

        // Exporta metadados adicionais em JSON
        writeMetadataJson(source, clip, outputFile, jsonMetadataFile);

        ClipCandidate completedClip = new ClipCandidate(
                clip.id(),
                clip.videoSourceId(),
                clip.title(),
                clip.hook(),
                clip.startSeconds(),
                clip.endSeconds(),
                clip.viralityScore(),
                clip.reasoning(),
                clip.suggestedCaption(),
                clip.hashtags(),
                orientation,
                clip.hasSubtitles(),
                outputFile.toAbsolutePath().toString(),
                jsonMetadataFile.toAbsolutePath().toString(),
                ProcessingStatus.COMPLETED,
                clip.thumbnailTimestamp(),
                clip.thumbnailHeadline(),
                clip.thumbnailPath(),
                clip.rawFramePath()
        );

        EventBus.getInstance().publish(new ClipprEvent.LogMessage("Corte renderizado com sucesso: " + outputFile.getFileName(), false));
        EventBus.getInstance().publish(new ClipprEvent.ClipRenderCompleted(completedClip));

        return completedClip;
    }

    private List<String> buildFfmpegCommand(
            Path sourceVideo,
            Path outputFile,
            double startSeconds,
            double duration,
            OrientationMode orientation,
            Path assSubtitles,
            int cpuThreads,
            boolean useBlurredBackground
    ) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-y"); // Sobrescreve destino se existir
        cmd.add("-ss");
        cmd.add(String.format(java.util.Locale.US, "%.3f", startSeconds));
        cmd.add("-i");
        cmd.add(sourceVideo.toString());
        cmd.add("-t");
        cmd.add(String.format(java.util.Locale.US, "%.3f", duration));

        // Filtro de vídeo (Fundo Desfocado / Crop 9:16 ou 16:9 + Legendas)
        StringBuilder filterGraph = new StringBuilder();

        if (orientation == OrientationMode.VERTICAL_9_16) {
            if (useBlurredBackground) {
                filterGraph.append("split[v1][v2];[v1]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=20:5[bg];[v2]scale=1080:1920:force_original_aspect_ratio=decrease[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2");
            } else {
                filterGraph.append("crop=ih*(9/16):ih,scale=1080:1920");
            }
        } else {
            filterGraph.append("scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2");
        }

        if (assSubtitles != null && Files.exists(assSubtitles)) {
            String escapedAss = assSubtitles.toAbsolutePath().toString().replace("\\", "/").replace(":", "\\:");
            filterGraph.append(",ass='").append(escapedAss).append("'");
        }

        cmd.add("-vf");
        cmd.add(filterGraph.toString());

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("fast");
        cmd.add("-crf");
        cmd.add("20");

        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");

        // Uso de 100% da CPU (-threads 0) ou valor configurado
        cmd.add("-threads");
        cmd.add(String.valueOf(cpuThreads <= 0 ? 0 : cpuThreads));

        cmd.add(outputFile.toString());
        return cmd;
    }

    private void writeMetadataJson(VideoSource source, ClipCandidate clip, Path videoFile, Path targetJson) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("title", clip.title());
            root.put("hook", clip.hook());
            root.put("viralityScore", clip.viralityScore());
            root.put("startSeconds", clip.startSeconds());
            root.put("endSeconds", clip.endSeconds());
            root.put("durationSeconds", clip.durationSeconds());
            root.put("suggestedCaption", clip.suggestedCaption());
            root.put("reasoning", clip.reasoning());
            root.put("orientation", clip.orientation() != null ? clip.orientation().name() : "VERTICAL_9_16");
            root.put("sourceUrl", source.url());
            root.put("sourceVideoId", source.videoId());
            root.put("author", source.author());
            root.put("exportedAt", Instant.now().toString());
            root.put("videoFilePath", videoFile.toAbsolutePath().toString());
            if (clip.thumbnailTimestamp() != null) {
                root.put("thumbnailTimestamp", clip.thumbnailTimestamp());
            }
            if (clip.thumbnailHeadline() != null) {
                root.put("thumbnailHeadline", clip.thumbnailHeadline());
            }
            if (clip.thumbnailPath() != null) {
                root.put("thumbnailPath", clip.thumbnailPath());
            }
            if (clip.rawFramePath() != null) {
                root.put("rawFramePath", clip.rawFramePath());
            }

            ArrayNode tagsNode = root.putArray("hashtags");
            if (clip.hashtags() != null) {
                for (String tag : clip.hashtags()) tagsNode.add(tag);
            }

            MAPPER.writeValue(targetJson.toFile(), root);
            logger.info("Metadados exportados com sucesso em: {}", targetJson);
        } catch (Exception e) {
            logger.warn("Não foi possível salvar o arquivo de metadados {}: {}", targetJson, e.getMessage());
        }
    }
}
