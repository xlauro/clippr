package com.laurosantos.clippr.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpManager {

    private static final Logger logger = LoggerFactory.getLogger(YtDlpManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");
    private static final String GITHUB_YTDLP_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";

    private final Path binaryPath;

    public record VideoMetadata(String id, String title, String author, double durationSeconds) {}
    public record TranscriptResult(VideoMetadata metadata, List<TranscriptSegment> segments, String formattedTranscript, Path vttFile) {}

    public YtDlpManager() {
        this.binaryPath = resolveOrDownloadBinary();
    }

    public YtDlpManager(Path customBinaryPath) {
        this.binaryPath = customBinaryPath;
    }

    public Path getBinaryPath() {
        return binaryPath;
    }

    public static synchronized Path resolveOrDownloadBinary() {
        // 1. Verifica no PATH do sistema
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, "yt-dlp");
                if (Files.isExecutable(candidate)) {
                    logger.info("yt-dlp encontrado no PATH: {}", candidate);
                    return candidate;
                }
            }
        }

        // 2. Verifica na pasta local bin/
        Path localBin = Path.of("bin", "yt-dlp").toAbsolutePath();
        if (Files.isExecutable(localBin)) {
            logger.info("yt-dlp encontrado na pasta bin/: {}", localBin);
            return localBin;
        }

        // 3. Verifica em ~/.clippr/bin/yt-dlp
        String userHome = System.getProperty("user.home", ".");
        Path userClipprBin = Path.of(userHome, ".clippr", "bin", "yt-dlp").toAbsolutePath();
        if (Files.isExecutable(userClipprBin)) {
            logger.info("yt-dlp encontrado em ~/.clippr/bin/: {}", userClipprBin);
            return userClipprBin;
        }

        // 4. Se não existir, faz download automático para ~/.clippr/bin/yt-dlp
        return downloadStandaloneBinary(userClipprBin);
    }

    private static Path downloadStandaloneBinary(Path targetPath) {
        try {
            logger.info("Baixando binário oficial do yt-dlp para {}", targetPath);
            EventBus.getInstance().publish(new ClipprEvent.LogMessage("Baixando ferramenta yt-dlp para processamento de vídeos...", false));

            Files.createDirectories(targetPath.getParent());
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_YTDLP_URL))
                    .GET()
                    .build();

            Path tempDownload = Files.createTempFile("yt-dlp-dl", ".tmp");
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempDownload));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.move(tempDownload, targetPath, StandardCopyOption.REPLACE_EXISTING);
                File file = targetPath.toFile();
                file.setExecutable(true, false);
                logger.info("yt-dlp baixado com sucesso e marcado como executável.");
                EventBus.getInstance().publish(new ClipprEvent.LogMessage("yt-dlp instalado com sucesso.", false));
                return targetPath;
            } else {
                throw new IllegalStateException("Falha ao baixar yt-dlp: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Erro ao baixar binário yt-dlp: {}", e.getMessage(), e);
            EventBus.getInstance().publish(new ClipprEvent.PipelineError("DOWNLOADER", "Não foi possível baixar o yt-dlp automaticamente", e));
            throw new RuntimeException("Não foi possível inicializar yt-dlp: " + e.getMessage(), e);
        }
    }

    public TranscriptResult fetchMetadataAndTranscript(String url) throws Exception {
        logger.info("Extraindo metadados e transcrição do YouTube: {}", url);
        EventBus.getInstance().publish(new ClipprEvent.LogMessage("Extraindo transcrição e timestamps do vídeo: " + url, false));

        Path tempDir = Files.createDirectories(Path.of("temp", "transcripts"));
        String outputTemplate = tempDir.resolve("%(id)s.%(ext)s").toString();

        ProcessBuilder pb = new ProcessBuilder(
                binaryPath.toString(),
                "--dump-single-json",
                "--skip-download",
                "--write-auto-sub",
                "--write-sub",
                "--sub-lang", "pt.*,en.*,pt,en",
                "--sub-format", "vtt",
                "--output", outputTemplate,
                url
        );

        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder jsonOutput = new StringBuilder();
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonOutput.append(line).append("\n");
                }
            } catch (Exception ignored) {}
        });

        StringBuilder stderrOutput = new StringBuilder();
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    EventBus.getInstance().publish(new ClipprEvent.LogMessage("[yt-dlp] " + line, true));
                }
            } catch (Exception ignored) {}
        });

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String err = stderrOutput.toString();
            logger.error("Falha ao extrair dados do YouTube (exitCode={}): {}", exitCode, err);
            throw new RuntimeException("Falha no yt-dlp ao processar URL: " + err);
        }

        JsonNode root = MAPPER.readTree(jsonOutput.toString());
        String id = root.path("id").asText();
        String title = root.path("title").asText("Vídeo sem título");
        String uploader = root.path("uploader").asText(root.path("channel").asText("Canal Desconhecido"));
        double duration = root.path("duration").asDouble(0.0);

        VideoMetadata metadata = new VideoMetadata(id, title, uploader, duration);

        // Localiza o arquivo .vtt gerado na pasta tempDir
        Path foundVtt = null;
        try (var stream = Files.list(tempDir)) {
            for (Path path : stream.toList()) {
                String filename = path.getFileName().toString();
                if (filename.startsWith(id) && filename.endsWith(".vtt")) {
                    foundVtt = path;
                    break;
                }
            }
        }

        List<TranscriptSegment> segments;
        String formattedTranscript;

        if (foundVtt != null && Files.exists(foundVtt)) {
            String vttContent = Files.readString(foundVtt);
            segments = TranscriptParser.parseVtt(vttContent);
            formattedTranscript = TranscriptParser.formatForAi(segments);
            logger.info("Transcrição extraída com sucesso: {} segmentos encontrados.", segments.size());
            EventBus.getInstance().publish(new ClipprEvent.LogMessage("Transcrição obtida com sucesso: %d trechos identificados.".formatted(segments.size()), false));
        } else {
            logger.warn("Nenhuma legenda encontrada para o vídeo {}.", id);
            segments = List.of();
            formattedTranscript = "";
            EventBus.getInstance().publish(new ClipprEvent.LogMessage("Aviso: Nenhuma legenda automática ou manual disponível para este vídeo.", false));
        }

        return new TranscriptResult(metadata, segments, formattedTranscript, foundVtt);
    }

    public Path downloadFullVideo(String url, Path destinationFile, java.util.function.Consumer<Double> progressConsumer) throws Exception {
        logger.info("Iniciando download do vídeo de alta qualidade: {} -> {}", url, destinationFile);
        EventBus.getInstance().publish(new ClipprEvent.LogMessage("Iniciando download do vídeo completo em alta qualidade...", false));

        Files.createDirectories(destinationFile.getParent());

        ProcessBuilder pb = new ProcessBuilder(
                binaryPath.toString(),
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "--output", destinationFile.toString(),
                "--newline",
                url
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                EventBus.getInstance().publish(new ClipprEvent.LogMessage("[yt-dlp download] " + line, true));
                Matcher matcher = PROGRESS_PATTERN.matcher(line);
                if (matcher.find()) {
                    double percent = Double.parseDouble(matcher.group(1));
                    if (progressConsumer != null) {
                        progressConsumer.accept(percent / 100.0);
                    }
                    EventBus.getInstance().publish(new ClipprEvent.ProgressUpdated("DOWNLOAD_VIDEO", percent / 100.0, line));
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Erro ao baixar vídeo via yt-dlp (exit code: " + exitCode + ")");
        }

        logger.info("Download concluído com sucesso: {}", destinationFile);
        EventBus.getInstance().publish(new ClipprEvent.LogMessage("Download do vídeo finalizado: " + destinationFile.getFileName(), false));
        return destinationFile;
    }
}
