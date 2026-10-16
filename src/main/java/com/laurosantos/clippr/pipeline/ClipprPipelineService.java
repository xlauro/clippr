package com.laurosantos.clippr.pipeline;

import com.laurosantos.clippr.ai.GeminiClient;
import com.laurosantos.clippr.domain.*;
import com.laurosantos.clippr.downloader.TranscriptSegment;
import com.laurosantos.clippr.downloader.YtDlpManager;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import com.laurosantos.clippr.ffmpeg.FfmpegExecutor;
import com.laurosantos.clippr.ffmpeg.ThumbnailGenerator;
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
    private final ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator();

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
                eventBus.publish(new ClipprEvent.ProgressUpdated(0.1, "Obtendo transcrição e metadados..."));
                db.updateVideoStatus(source.id(), ProcessingStatus.FETCHING_TRANSCRIPT, null);
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.FETCHING_TRANSCRIPT, null)));

                // 1. Extração ou Recuperação de Metadados e Transcrição
                List<TranscriptSegment> segments = cachedSegments.get(source.id());
                if ((segments == null || segments.isEmpty()) && source.transcriptPath() != null) {
                    Path vtt = Path.of(source.transcriptPath());
                    if (Files.exists(vtt)) {
                        try {
                            segments = com.laurosantos.clippr.downloader.TranscriptParser.parseVtt(Files.readString(vtt));
                            if (!segments.isEmpty()) {
                                cachedSegments.put(source.id(), segments);
                            }
                        } catch (Exception e) {
                            logger.warn("Falha ao recuperar legenda do disco: {}", e.getMessage());
                        }
                    }
                }

                VideoSource updatedSource = source;
                if (segments == null || segments.isEmpty()) {
                    eventBus.publish(new ClipprEvent.LogMessage("Iniciando processamento de: " + source.url(), false));
                    eventBus.publish(new ClipprEvent.ProgressUpdated(0.1, "Obtendo transcrição e metadados..."));
                    db.updateVideoStatus(source.id(), ProcessingStatus.FETCHING_TRANSCRIPT, null);
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.FETCHING_TRANSCRIPT, null)));

                    var transcriptResult = ytDlpManager.fetchMetadataAndTranscript(source.url());
                    updatedSource = new VideoSource(
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
                        eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource.withStatus(ProcessingStatus.FAILED, msg)));
                        eventBus.publish(new ClipprEvent.LogMessage("Falha: " + msg, false));
                        eventBus.publish(new ClipprEvent.ProgressUpdated(0.0, "Falha: Sem transcrição"));
                        return;
                    }

                    segments = transcriptResult.segments();
                    cachedSegments.put(source.id(), segments);
                } else {
                    eventBus.publish(new ClipprEvent.LogMessage("Reaproveitando transcrição existente (" + segments.size() + " trechos) para: " + (source.title() != null ? source.title() : source.url()), false));
                    db.updateVideoStatus(source.id(), ProcessingStatus.ANALYZING_AI, null);
                    updatedSource = source.withStatus(ProcessingStatus.ANALYZING_AI, null);
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource));
                }

                // 2. Avaliação de Viralização via Gemini 3.8 Flash
                String apiKey = settings.geminiApiKey();
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = com.laurosantos.clippr.ai.GeminiApiKeyResolver.resolveApiKey();
                }

                if (apiKey.isBlank()) {
                    String msg = "Chave da API do Gemini não informada. Configure na aba de Configurações.";
                    db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, msg);
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource.withStatus(ProcessingStatus.FAILED, msg)));
                    eventBus.publish(new ClipprEvent.LogMessage("Erro: " + msg, false));
                    eventBus.publish(new ClipprEvent.ProgressUpdated(0.0, "Erro: Chave de API ausente"));
                    return;
                }

                eventBus.publish(new ClipprEvent.ProgressUpdated(0.4, "Analisando momentos virais com IA Gemini..."));
                GeminiClient gemini = new GeminiClient(apiKey, settings.geminiModel());
                var analysis = gemini.analyzeTranscript(
                        source.id(),
                        updatedSource.title(),
                        updatedSource.author(),
                        segments,
                        settings.minViralityScore()
                );

                List<ClipCandidate> approvedClips = analysis.approvedClips();
                if (approvedClips.isEmpty()) {
                    String msg = "Nenhum momento atingiu a nota mínima de viralização (" + settings.minViralityScore() + ")";
                    db.updateVideoStatus(source.id(), ProcessingStatus.DISCARDED_LOW_SCORE, msg);
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource.withStatus(ProcessingStatus.DISCARDED_LOW_SCORE, msg)));
                    eventBus.publish(new ClipprEvent.LogMessage("Nenhum corte aprovado (todos com nota < " + settings.minViralityScore() + "). Vídeo descartado sem download.", false));
                    eventBus.publish(new ClipprEvent.ProgressUpdated(1.0, "Vídeo descartado (nota baixa)"));
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
                            c.id(), source.id(), c.title(), c.hook(), c.startSeconds(), c.endSeconds(),
                            c.viralityScore(), c.reasoning(), c.suggestedCaption(), c.hashtags(),
                            resolvedOrientation, settings.burnSubtitles(), c.outputFilePath(), c.metadataJsonPath(),
                            ProcessingStatus.PENDING_APPROVAL,
                            c.thumbnailTimestamp(), c.thumbnailHeadline(), c.thumbnailPath(), c.rawFramePath()
                    ));
                }

                List<ClipCandidate> savedClips = db.saveClips(source.id(), orientedClips);

                // Notifica a UI com os clipes devidamente persistidos e com IDs
                eventBus.publish(new ClipprEvent.ClipsEvaluated(source.id(), savedClips, analysis.discardedClips().size()));
                eventBus.publish(new ClipprEvent.ProgressUpdated(0.65, "%d corte(s) detectado(s) com nota >= %d".formatted(savedClips.size(), settings.minViralityScore())));

                // 3. Processamento Automático ou Aguarda Aprovação
                if (settings.autoProcessAboveThreshold()) {
                    eventBus.publish(new ClipprEvent.LogMessage("Modo automático ativado: baixando vídeo e iniciando cortes...", false));
                    renderClipsForSource(updatedSource, savedClips, settings);
                } else {
                    db.updateVideoStatus(source.id(), ProcessingStatus.PENDING_APPROVAL, null);
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(updatedSource.withStatus(ProcessingStatus.PENDING_APPROVAL, null)));
                    eventBus.publish(new ClipprEvent.LogMessage("Cortes prontos para revisão humana. Clique em 'Renderizar' na aba de Cortes.", false));
                }

            } catch (Exception ex) {
                logger.error("Erro no processamento do vídeo {}: {}", source.url(), ex.getMessage(), ex);
                db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, ex.getMessage());
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.FAILED, ex.getMessage())));
                eventBus.publish(new ClipprEvent.PipelineError("PIPELINE", "Erro ao processar vídeo: " + ex.getMessage(), ex));
                eventBus.publish(new ClipprEvent.ProgressUpdated(0.0, "Erro: " + ex.getMessage()));
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
                    eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.DOWNLOADING_VIDEO, null)));
                    eventBus.publish(new ClipprEvent.ProgressUpdated(0.7, "Baixando vídeo completo do YouTube..."));

                    Path tempDir = Files.createDirectories(Path.of("temp", "videos"));
                    Path target = tempDir.resolve("%s.mp4".formatted(source.videoId()));
                    videoFile = ytDlpManager.downloadFullVideo(source.url(), target, null);
                    cachedVideoFiles.put(source.id(), videoFile);
                }

                db.updateVideoStatus(source.id(), ProcessingStatus.CUTTING_FFMPEG, null);
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.CUTTING_FFMPEG, null)));
                eventBus.publish(new ClipprEvent.ProgressUpdated(0.85, "Renderizando cortes com FFmpeg (100% CPU)..."));

                Path outputDir = Path.of(settings.outputDirectory());
                List<TranscriptSegment> segments = cachedSegments.getOrDefault(source.id(), List.of());

                for (ClipCandidate clip : clips) {
                    ClipCandidate rendered = ffmpegExecutor.renderClip(
                            source,
                            clip,
                            videoFile,
                            outputDir,
                            segments,
                            settings.cpuThreads(),
                            true // Fundo desfocado para 9:16
                    );
                    db.updateClipOutput(clip.id(), rendered.outputFilePath(), rendered.metadataJsonPath(), ProcessingStatus.COMPLETED);

                    // Geração automática de thumbnail para cortes longos, horizontais ou com thumbnail configurada
                    boolean isHorizontal = clip.orientation() == OrientationMode.HORIZONTAL_16_9;
                    boolean hasCustomThumb = clip.thumbnailHeadline() != null || clip.thumbnailTimestamp() != null || clip.thumbnailPath() != null;
                    if (clip.isLongClip() || isHorizontal || hasCustomThumb) {
                        try {
                            String headline = clip.thumbnailHeadline() != null && !clip.thumbnailHeadline().isBlank()
                                    ? clip.thumbnailHeadline()
                                    : (clip.hook() != null && !clip.hook().isBlank() ? clip.hook() : clip.title());
                            double thumbTimestamp = clip.thumbnailTimestamp() != null
                                    ? clip.thumbnailTimestamp()
                                    : clip.startSeconds() + Math.min(2.0, clip.durationSeconds() / 2.0);
                            String baseFileName = "thumb_clip_" + (clip.id() != null ? clip.id() : System.currentTimeMillis());

                            ThumbnailGenerator.ThumbnailResult thumbResult = thumbnailGenerator.generateThumbnail(
                                    videoFile,
                                    thumbTimestamp,
                                    headline,
                                    outputDir,
                                    baseFileName
                            );

                            ClipCandidate withThumb = rendered.withThumbnail(
                                    thumbTimestamp,
                                    headline,
                                    thumbResult.finalThumbnailPath().toAbsolutePath().toString(),
                                    thumbResult.rawFramePath().toAbsolutePath().toString()
                            );

                            if (clip.id() != null) {
                                db.updateClipThumbnail(
                                        clip.id(),
                                        thumbTimestamp,
                                        headline,
                                        withThumb.thumbnailPath(),
                                        withThumb.rawFramePath()
                                );
                            }
                            eventBus.publish(new ClipprEvent.ThumbnailGenerated(withThumb, thumbResult.finalThumbnailPath(), thumbResult.rawFramePath()));
                            eventBus.publish(new ClipprEvent.LogMessage("Thumbnail gerada para o corte: " + clip.title(), false));
                        } catch (Exception exThumb) {
                            logger.warn("Falha ao gerar thumbnail para o corte {}: {}", clip.title(), exThumb.getMessage());
                            eventBus.publish(new ClipprEvent.LogMessage("Aviso: Falha ao gerar thumbnail para " + clip.title() + ": " + exThumb.getMessage(), true));
                        }
                    }
                }

                db.updateVideoStatus(source.id(), ProcessingStatus.COMPLETED, null);
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.COMPLETED, null)));
                eventBus.publish(new ClipprEvent.ProgressUpdated(1.0, "Todos os cortes concluídos com sucesso!"));
                eventBus.publish(new ClipprEvent.LogMessage("Todos os cortes do vídeo \"%s\" foram concluídos e salvos em %s".formatted(source.title(), outputDir), false));

            } catch (Exception e) {
                logger.error("Erro na renderização dos cortes de {}: {}", source.title(), e.getMessage(), e);
                db.updateVideoStatus(source.id(), ProcessingStatus.FAILED, e.getMessage());
                eventBus.publish(new ClipprEvent.VideoSourceUpdated(source.withStatus(ProcessingStatus.FAILED, e.getMessage())));
                eventBus.publish(new ClipprEvent.PipelineError("RENDER", "Erro ao renderizar cortes: " + e.getMessage(), e));
                eventBus.publish(new ClipprEvent.ProgressUpdated(0.0, "Erro na renderização: " + e.getMessage()));
            }
        });
    }

    public ThumbnailGenerator getThumbnailGenerator() {
        return thumbnailGenerator;
    }

    public ClipCandidate generateThumbnailForClipOnDemand(VideoSource source, ClipCandidate clip, double timestamp, String headline, Path outputDir) {
        Thread.ofVirtual().start(() -> {
            try {
                Path videoFile = getOrCreateVideoFile(source);
                String baseFileName = "thumb_clip_" + (clip.id() != null ? clip.id() : System.currentTimeMillis());
                ThumbnailGenerator.ThumbnailResult thumbResult = thumbnailGenerator.generateThumbnail(
                        videoFile,
                        timestamp,
                        headline,
                        outputDir,
                        baseFileName
                );

                ClipCandidate updated = clip.withThumbnail(
                        timestamp,
                        headline,
                        thumbResult.finalThumbnailPath().toAbsolutePath().toString(),
                        thumbResult.rawFramePath().toAbsolutePath().toString()
                );

                if (clip.id() != null) {
                    db.updateClipThumbnail(clip.id(), timestamp, headline, updated.thumbnailPath(), updated.rawFramePath());
                }

                eventBus.publish(new ClipprEvent.ThumbnailGenerated(updated, thumbResult.finalThumbnailPath(), thumbResult.rawFramePath()));
                eventBus.publish(new ClipprEvent.LogMessage("Thumbnail gerada sob demanda para o corte: " + clip.title(), false));
            } catch (Exception e) {
                logger.error("Erro ao gerar thumbnail sob demanda para clipe {}: {}", clip.title(), e.getMessage(), e);
                eventBus.publish(new ClipprEvent.PipelineError("THUMBNAIL", "Erro ao gerar thumbnail: " + e.getMessage(), e));
            }
        });
        return clip;
    }
}
