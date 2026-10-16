package com.laurosantos.clippr.pipeline;

import com.laurosantos.clippr.ai.GeminiClient;
import com.laurosantos.clippr.domain.*;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import com.laurosantos.clippr.downloader.YtDlpManager;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import com.laurosantos.clippr.ffmpeg.FfmpegExecutor;
import com.laurosantos.clippr.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClipprPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(ClipprPipelineService.class);
    private static final ClipprPipelineService INSTANCE = new ClipprPipelineService();

    private final DatabaseManager db = DatabaseManager.getInstance();
    private final EventBus eventBus = EventBus.getInstance();
    private final YtDlpManager ytDlpManager = new YtDlpManager();
    private final FfmpegExecutor ffmpegExecutor = new FfmpegExecutor();

    private final Map<Long, List<TranscriptSegment>> cachedSegments = new ConcurrentHashMap<>();
    private final Map<Long, Path> cachedVideoFiles = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private ClipprPipelineService() {}

    public static ClipprPipelineService getInstance() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public Path getOrCreateVideoFile(VideoSource source) throws Exception {
        Path existing = cachedVideoFiles.get(source.id());
        if (existing != null && Files.exists(existing)) {
            return existing;
        }

        Path localCandidate = Path.of("temp", "videos", "%s.mp4".formatted(source.videoId()));
        if (Files.exists(localCandidate)) {
            cachedVideoFiles.put(source.id(), localCandidate);
            return localCandidate;
        }

        Path target = Files.createDirectories(Path.of("temp", "videos")).resolve("%s.mp4".formatted(source.videoId()));
        Path downloaded = ytDlpManager.downloadFullVideo(source.url(), target, null);
        cachedVideoFiles.put(source.id(), downloaded);
        return downloaded;
    }

    public void processVideoSource(VideoSource source, AppSettings settings) {
        Thread.ofVirtual().start(() -> {
            try {
                isRunning.set(true);
                eventBus.publish(new ClipprEvent.LogMessage("Iniciando processamento de: " + source.url(), false));
                db.updateVideoStatus(source.id(), ProcessingStatus.FETCHING_TRANSCRIPT, null);
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(new VideoSource(
                        source.id(), source.url(), source.videoId(), source.title(), source.author(),
                        source.durationSeconds(), source.transcriptPath(), source.localVideoPath(),
                        ProcessingStatus.FETCHING_TRANSCRIPT, null, source.createdAt()
                )));

                // 1. Extração de Metadados e Transcrição em 2 segundos
                var transcriptResult = ytDlpManager.fetchMetadataAndTranscript(source.url());
                VideoSource updatedSource = new VideoSource(
                        source.id(),
                        source.url(),
                        transcriptResult.metadata().id(),
                        transcriptResult.metadata().title(),
                        transcriptResult.metadata().author(),
                        transcriptResult.metadata().durationSeconds(),
                        transcriptResult.vttFile() != null ? transcriptResult.vttFile().toString() : null,
                        null,
                        ProcessingStatus.ANALYZING_AI,
                        null,
                        source.createdAt()
                );
                db.saveVideo(updatedSource);
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource));

                if (transcriptResult.segments().isEmpty()) {
                    String msg = "Nenhuma transcrição encontrada para o vídeo.";
                    db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, msg);
                    eventBus.publish(new ClipprEvent.LogMessage("Falha: " + msg, false));
                    return;
                }

                cachedSegments.put(source.id(), transcriptResult.segments());

                // 2. Avaliação de Viralização via Gemini 3.8 Flash
                String apiKey = settings.geminiApiKey();
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getenv().getOrDefault("GEMINI_API_KEY", "");
                }

                if (apiKey.isBlank()) {
                    String msg = "Chave da API do Gemini não informada. Configure nas opções do sistema.";
                    db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, msg);
                    eventBus.publish(new ClipprEvent.LogMessage("Erro: " + msg, false));
                    return;
                }

                GeminiClient gemini = new GeminiClient(apiKey, settings.geminiModel());
                var analysis = gemini.analyzeTranscript(
                        source.id(),
                        updatedSource.title(),
                        updatedSource.author(),
                        transcriptResult.segments(),
                        settings.minViralityScore()
                );

                List<ClipCandidate> approvedClips = analysis.approvedClips();
                if (approvedClips.isEmpty()) {
                    db.updateVideoStatus(source.id(), ProcessingStatus.DISCARDED_LOW_SCORE, "Nenhum momento atingiu a nota mínima de viralização (" + settings.minViralityScore() + ")");
                    eventBus.publish(new ClipprEvent.LogMessage("Nenhum corte aprovado (todos com nota < " + settings.minViralityScore() + "). Vídeo descartado sem download.", false));
                    return;
                }

                // Ajusta orientação configurada (se AUTO, calcula baseado na duração)
                List<ClipCandidate> orientedClips = new ArrayList<>();
                for (ClipCandidate c : approvedClips) {
                    OrientationMode resolvedOrientation = OrientationMode.resolveForDuration(
                            settings.defaultOrientation(),
                            c.durationSeconds(),
                            settings.autoVerticalMaxDurationSeconds()
                    );
                    orientedClips.add(new ClipCandidate(
                            c.id(), c.videoSourceId(), c.title(), c.hook(), c.startSeconds(), c.endSeconds(),
                            c.viralityScore(), c.reasoning(), c.suggestedCaption(), c.hashtags(),
                            resolvedOrientation, settings.burnSubtitles(), c.outputFilePath(), c.metadataJsonPath(),
                            ProcessingStatus.PENDING_APPROVAL
                    ));
                }

                List<ClipCandidate> savedClips = db.saveClips(source.id(), orientedClips);

                // 3. Processamento Automático ou Aguarda Aprovação
                if (settings.autoProcessAboveThreshold()) {
                    eventBus.publish(new ClipprEvent.LogMessage("Modo automático ativado: baixando vídeo e iniciando cortes...", false));
                    renderClipsForSource(updatedSource, savedClips, settings);
                } else {
                    db.updateVideoStatus(source.id(), ProcessingStatus.PENDING_APPROVAL, null);
                    eventBus.publish(new ClipprEvent.LogMessage("Cortes prontos para revisão humana. Clique em 'Renderizar' na aba de Cortes.", false));
                }

            } catch (Exception ex) {
                logger.error("Erro no processamento do vídeo {}: {}", source.url(), ex.getMessage(), ex);
                db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, ex.getMessage());
                eventBus.publish(new ClipprEvent.PipelineError("PIPELINE", "Erro ao processar vídeo: " + ex.getMessage(), ex));
            } finally {
                isRunning.set(false);
            }
        });
    }

    public void renderClipsForSource(VideoSource source, List<ClipCandidate> clips, AppSettings settings) {
        Thread.ofVirtual().start(() -> {
            try {
                Path videoFile = cachedVideoFiles.get(source.id());

                // Se o vídeo ainda não foi baixado, baixa agora
                if (videoFile == null || !Files.exists(videoFile)) {
                    db.updateVideoStatus(source.id(), ProcessingStatus.DOWNLOADING_VIDEO, null);
                    Path tempDir = Files.createDirectories(Path.of("temp", "videos"));
                    Path target = tempDir.resolve("%s.mp4".formatted(source.videoId()));
                    videoFile = ytDlpManager.downloadFullVideo(source.url(), target, null);
                    cachedVideoFiles.put(source.id(), videoFile);
                }

                db.updateVideoStatus(source.id(), ProcessingStatus.CUTTING_FFMPEG, null);
                Path outputDir = Path.of(settings.outputDirectory());
                List<TranscriptSegment> segments = cachedSegments.getOrDefault(source.id(), List.of());

                for (ClipCandidate clip : clips) {
                    ffmpegExecutor.renderClip(
                            source,
                            clip,
                            videoFile,
                            outputDir,
                            segments,
                            settings.cpuThreads(),
                            true // Fundo desfocado para 9:16
                    );
                }

                db.updateVideoStatus(source.id(), ProcessingStatus.COMPLETED, null);
                eventBus.publish(new ClipprEvent.LogMessage("Todos os cortes do vídeo \"%s\" foram concluídos e salvos em %s".formatted(source.title(), outputDir), false));

            } catch (Exception e) {
                logger.error("Erro na renderização dos cortes de {}: {}", source.title(), e.getMessage(), e);
                db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, e.getMessage());
                eventBus.publish(new ClipprEvent.PipelineError("RENDER", "Erro ao renderizar cortes: " + e.getMessage(), e));
            }
        });
    }
}
