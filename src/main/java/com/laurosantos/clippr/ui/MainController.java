package com.laurosantos.clippr.ui;

import com.laurosantos.clippr.ai.GeminiApiKeyResolver;
import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.domain.VideoSource;
import com.laurosantos.clippr.downloader.YtDlpManager;
import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import com.laurosantos.clippr.ffmpeg.FfmpegExecutor;
import com.laurosantos.clippr.persistence.DatabaseManager;
import com.laurosantos.clippr.pipeline.ClipprPipelineService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainController {

    public MainController() {}

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private Label dbStatusBadge;
    @FXML private Label ffmpegStatusBadge;
    @FXML private Label geminiStatusBadge;
    @FXML private Label diagPostgresLabel;
    @FXML private Label diagFfmpegLabel;
    @FXML private Label diagYtDlpLabel;
    @FXML private Label diagGeminiLabel;

    @FXML private TabPane tabPane;
    @FXML private TextField linkInputField;
    @FXML private TableView<VideoSource> videoTableView;
    @FXML private TableColumn<VideoSource, String> colVideoTitle;
    @FXML private TableColumn<VideoSource, String> colVideoAuthor;
    @FXML private TableColumn<VideoSource, String> colVideoDuration;
    @FXML private TableColumn<VideoSource, String> colVideoStatus;

    @FXML private CheckBox chkSubtitles;
    @FXML private CheckBox chkAutoProcess;
    @FXML private ComboBox<OrientationMode> comboOrientation;
    @FXML private Button btnStartPipeline;

    @FXML private TableView<ClipCandidate> clipTableView;
    @FXML private TableColumn<ClipCandidate, String> colClipScore;
    @FXML private TableColumn<ClipCandidate, String> colClipTitle;
    @FXML private TableColumn<ClipCandidate, String> colClipHook;
    @FXML private TableColumn<ClipCandidate, String> colClipTime;
    @FXML private TableColumn<ClipCandidate, String> colClipOrientation;
    @FXML private TableColumn<ClipCandidate, String> colClipStatus;

    // Player de Vídeo e Ajuste Fino
    @FXML private StackPane playerContainer;
    @FXML private MediaView mediaView;
    @FXML private Label playerPlaceholderLabel;
    @FXML private Slider timeSlider;
    @FXML private Label timeLabel;
    @FXML private Button btnPlayPause;
    @FXML private Slider volumeSlider;
    @FXML private Label lblStartSeconds;
    @FXML private Label lblEndSeconds;

    @FXML private PasswordField geminiApiKeyField;
    @FXML private Label geminiApiKeyStatusLabel;
    @FXML private TextField outputDirField;

    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private CheckBox chkDebugMode;
    @FXML private TextArea logArea;

    private final ObservableList<VideoSource> videoList = FXCollections.observableArrayList();
    private final ObservableList<ClipCandidate> clipList = FXCollections.observableArrayList();
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final EventBus eventBus = EventBus.getInstance();
    private final ClipprPipelineService pipelineService = ClipprPipelineService.getInstance();

    private MediaPlayer mediaPlayer;
    private ClipCandidate currentPreviewClip;
    private double activeStartSeconds = 0.0;
    private double activeEndSeconds = 0.0;
    private boolean isPlayingRenderedFile = false;
    private boolean isSeeking = false;

    @FXML
    private void initialize() {
        setupTableColumns();
        setupTableContextMenu();
        setupOrientationComboBox();
        setupPlayerSelectionListener();
        loadSettings();
        registerEventBusListeners();
        checkEnvironmentDiagnostics();

        logMessage("Clippr inicializado com sucesso (Java 25 + JavaFX + AtlantaFX).", false);
    }

    private void setupTableColumns() {
        videoTableView.setItems(videoList);
        colVideoTitle.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().title() != null && !data.getValue().title().isBlank()
                        ? data.getValue().title() : data.getValue().url()
        ));
        colVideoAuthor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().author()));
        colVideoDuration.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().durationSeconds() > 0 ? String.format("%.0fs", data.getValue().durationSeconds()) : "-"
        ));
        colVideoStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().getDescription()));

        clipTableView.setItems(clipList);
        colClipScore.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().viralityScore() + " ⭐"));
        colClipTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));
        colClipHook.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().hook()));
        colClipTime.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.1fs - %.1fs (%.1fs)", data.getValue().startSeconds(), data.getValue().endSeconds(), data.getValue().durationSeconds())
        ));
        colClipOrientation.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().orientation() != null ? data.getValue().orientation().getLabel() : "Auto"
        ));
        colClipStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().getDescription()));
    }

    private void setupTableContextMenu() {
        // Menu de Contexto para a Fila de Vídeos
        ContextMenu videoMenu = new ContextMenu();
        MenuItem itemStartThis = new MenuItem("▶ Iniciar Processamento Deste Vídeo");
        itemStartThis.setOnAction(_ -> {
            VideoSource sel = videoTableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                AppSettings settings = buildCurrentSettings();
                logMessage("Iniciando vídeo selecionado: " + sel.url(), false);
                pipelineService.processVideoSource(sel, settings);
            }
        });

        MenuItem itemCopyUrl = new MenuItem("📋 Copiar Link");
        itemCopyUrl.setOnAction(_ -> {
            VideoSource sel = videoTableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(sel.url());
                clipboard.setContent(content);
                logMessage("Link copiado para a área de transferência: " + sel.url(), false);
            }
        });

        MenuItem itemOpenBrowser = new MenuItem("🌐 Abrir no Navegador");
        itemOpenBrowser.setOnAction(_ -> {
            VideoSource sel = videoTableView.getSelectionModel().getSelectedItem();
            if (sel != null && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(sel.url()));
                } catch (Exception ignored) {}
            }
        });

        MenuItem itemRemoveVideo = new MenuItem("🗑 Remover da Fila");
        itemRemoveVideo.setOnAction(_ -> {
            VideoSource sel = videoTableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                videoList.remove(sel);
                Thread.ofVirtual().start(() -> db.deleteVideo(sel.id()));
                logMessage("Vídeo removido da fila: " + sel.url(), false);
            }
        });

        videoMenu.getItems().addAll(itemStartThis, itemCopyUrl, itemOpenBrowser, new SeparatorMenuItem(), itemRemoveVideo);
        videoTableView.setContextMenu(videoMenu);

        // Menu de Contexto para a Tabela de Cortes
        ContextMenu clipMenu = new ContextMenu();
        MenuItem itemRenderThis = new MenuItem("▶ Renderizar Este Corte");
        itemRenderThis.setOnAction(_ -> onRenderSelectedClip());

        MenuItem itemOpenFolder = new MenuItem("📂 Abrir Pasta de Exportação");
        itemOpenFolder.setOnAction(_ -> {
            ClipCandidate sel = clipTableView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.outputFilePath() != null) {
                File f = new File(sel.outputFilePath());
                if (f.exists() && Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().open(f.getParentFile());
                    } catch (Exception ex) {
                        logMessage("Não foi possível abrir pasta: " + ex.getMessage(), false);
                    }
                }
            } else {
                showAlert("Não Renderizado", "Este corte ainda não foi renderizado em disco.");
            }
        });

        MenuItem itemCopyCaption = new MenuItem("📋 Copiar Legenda Sugerida");
        itemCopyCaption.setOnAction(_ -> {
            ClipCandidate sel = clipTableView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.suggestedCaption() != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(sel.suggestedCaption());
                clipboard.setContent(content);
                logMessage("Legenda copiada para a área de transferência.", false);
            }
        });

        MenuItem itemCopyHashtags = new MenuItem("🏷 Copiar Hashtags");
        itemCopyHashtags.setOnAction(_ -> {
            ClipCandidate sel = clipTableView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.hashtags() != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(String.join(" ", sel.hashtags()));
                clipboard.setContent(content);
                logMessage("Hashtags copiadas: " + String.join(" ", sel.hashtags()), false);
            }
        });

        MenuItem itemDeleteClip = new MenuItem("🗑 Excluir Corte");
        itemDeleteClip.setOnAction(_ -> {
            ClipCandidate sel = clipTableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                clipList.remove(sel);
                if (currentPreviewClip != null && currentPreviewClip.id() != null && currentPreviewClip.id().equals(sel.id())) {
                    disposeMediaPlayer();
                    playerPlaceholderLabel.setVisible(true);
                }
                Thread.ofVirtual().start(() -> db.deleteClip(sel.id()));
                logMessage("Corte removido: " + sel.title(), false);
            }
        });

        clipMenu.getItems().addAll(itemRenderThis, itemOpenFolder, new SeparatorMenuItem(), itemCopyCaption, itemCopyHashtags, new SeparatorMenuItem(), itemDeleteClip);
        clipTableView.setContextMenu(clipMenu);
    }

    private void setupOrientationComboBox() {
        comboOrientation.setItems(FXCollections.observableArrayList(OrientationMode.values()));
        comboOrientation.setValue(OrientationMode.AUTO);
    }

    private void setupPlayerSelectionListener() {
        if (playerContainer != null) {
            mediaView.fitWidthProperty().bind(playerContainer.widthProperty().subtract(16));
            mediaView.fitHeightProperty().bind(playerContainer.heightProperty().subtract(16));
        }

        clipTableView.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            if (selected != null) {
                loadClipInPlayer(selected);
            }
        });

        timeSlider.valueChangingProperty().addListener((_, _, changing) -> {
            isSeeking = changing;
            if (!changing && mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
            }
        });

        timeSlider.setOnMouseClicked(_ -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
            }
        });
    }

    private void loadClipInPlayer(ClipCandidate clip) {
        this.currentPreviewClip = clip;
        this.activeStartSeconds = clip.startSeconds();
        this.activeEndSeconds = clip.endSeconds();
        this.isPlayingRenderedFile = false;

        lblStartSeconds.setText(String.format("%.1fs", clip.startSeconds()));
        lblEndSeconds.setText(String.format("%.1fs", clip.endSeconds()));

        disposeMediaPlayer();

        // 1. Tenta carregar o clipe já renderizado
        if (clip.outputFilePath() != null && Files.exists(Path.of(clip.outputFilePath()))) {
            this.isPlayingRenderedFile = true;
            playMediaFile(new File(clip.outputFilePath()), 0.0, clip.durationSeconds());
            playerPlaceholderLabel.setVisible(false);
            return;
        }

        // 2. Se não renderizado, tenta carregar o vídeo fonte original
        VideoSource source = findSourceForClip(clip);
        if (source != null) {
            Path candidateSourceVideo = Path.of("temp", "videos", "%s.mp4".formatted(source.videoId()));
            if (Files.exists(candidateSourceVideo)) {
                playMediaFile(candidateSourceVideo.toFile(), clip.startSeconds(), clip.endSeconds());
                playerPlaceholderLabel.setVisible(false);
                return;
            }

            // 3. Vídeo fonte ainda não baixado: baixa sob demanda em Virtual Thread com feedback visual
            playerPlaceholderLabel.setText("⏳ Baixando vídeo original do YouTube para pré-visualização...");
            playerPlaceholderLabel.setVisible(true);

            Thread.ofVirtual().start(() -> {
                try {
                    Path downloaded = pipelineService.getOrCreateVideoFile(source);
                    Platform.runLater(() -> {
                        if (currentPreviewClip != null && currentPreviewClip.id() != null && currentPreviewClip.id().equals(clip.id())) {
                            playerPlaceholderLabel.setVisible(false);
                            playMediaFile(downloaded.toFile(), activeStartSeconds, activeEndSeconds);
                        }
                    });
                } catch (Exception ex) {
                    logger.error("Erro ao baixar vídeo para pré-visualização: {}", ex.getMessage());
                    Platform.runLater(() -> {
                        playerPlaceholderLabel.setText("Não foi possível carregar o vídeo: " + ex.getMessage());
                        playerPlaceholderLabel.setVisible(true);
                    });
                }
            });
            return;
        }

        playerPlaceholderLabel.setText("Vídeo de origem não encontrado na fila.");
        playerPlaceholderLabel.setVisible(true);
    }

    private void playMediaFile(File videoFile, double startOffsetSeconds, double endLimitSeconds) {
        try {
            Media media = new Media(videoFile.toURI().toString());
            media.setOnError(() -> {
                logger.error("Erro no arquivo de mídia ({}): {}", videoFile.getName(), media.getError());
                playerPlaceholderLabel.setText("Erro na mídia: " + (media.getError() != null ? media.getError().getMessage() : "Formato não suportado"));
                playerPlaceholderLabel.setVisible(true);
            });

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> {
                logger.error("Erro no MediaPlayer ({}): {}", videoFile.getName(), mediaPlayer.getError());
                playerPlaceholderLabel.setText("Erro no player: " + (mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Falha na reprodução"));
                playerPlaceholderLabel.setVisible(true);
            });
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setOnReady(() -> {
                double total = media.getDuration().toSeconds();
                timeSlider.setMin(0.0);
                timeSlider.setMax(total);

                if (startOffsetSeconds > 0) {
                    mediaPlayer.seek(Duration.seconds(startOffsetSeconds));
                    timeSlider.setValue(startOffsetSeconds);
                }

                updateTimeDisplay(startOffsetSeconds, total);
                mediaPlayer.volumeProperty().bind(volumeSlider.valueProperty());
            });

            mediaPlayer.currentTimeProperty().addListener((_, _, now) -> {
                if (now != null) {
                    double currentSec = now.toSeconds();
                    if (!isSeeking) {
                        timeSlider.setValue(currentSec);
                    }
                    if (mediaPlayer.getTotalDuration() != null) {
                        updateTimeDisplay(currentSec, mediaPlayer.getTotalDuration().toSeconds());
                    }

                    // Se for pré-visualização de trecho não cortado, respeita os limites dinâmicos
                    if (!isPlayingRenderedFile && activeEndSeconds > activeStartSeconds && currentSec >= activeEndSeconds) {
                        mediaPlayer.pause();
                        mediaPlayer.seek(Duration.seconds(activeStartSeconds));
                        btnPlayPause.setText("▶ Play");
                    }
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                btnPlayPause.setText("▶ Play");
                mediaPlayer.seek(Duration.seconds(isPlayingRenderedFile ? 0.0 : activeStartSeconds));
            });

            btnPlayPause.setText("▶ Play");
        } catch (Exception e) {
            logger.error("Erro ao inicializar player de vídeo: {}", e.getMessage(), e);
            playerPlaceholderLabel.setText("Não foi possível reproduzir o vídeo: " + e.getMessage());
            playerPlaceholderLabel.setVisible(true);
        }
    }

    private void updateTimeDisplay(double current, double total) {
        timeLabel.setText(String.format("%02d:%02d / %02d:%02d",
                (int) (current / 60), (int) (current % 60),
                (int) (total / 60), (int) (total % 60)
        ));
    }

    private void disposeMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        btnPlayPause.setText("▶ Play");
    }

    @FXML
    private void onTogglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            btnPlayPause.setText("▶ Play");
        } else {
            mediaPlayer.play();
            btnPlayPause.setText("⏸ Pausar");
        }
    }

    @FXML
    private void onAdjustStartMinus1() {
        adjustClipBounds(-1.0, 0.0);
    }

    @FXML
    private void onAdjustStartPlus1() {
        adjustClipBounds(1.0, 0.0);
    }

    @FXML
    private void onSetStartCurrent() {
        if (mediaPlayer == null || currentPreviewClip == null) {
            showAlert("Player Inativo", "Selecione e reproduza um corte para marcar a posição atual.");
            return;
        }
        if (isPlayingRenderedFile) {
            showAlert("Vídeo Já Renderizado", "Este corte já foi finalizado. O ajuste de marcadores aplica-se a vídeos antes da renderização.");
            return;
        }
        double currentSec = mediaPlayer.getCurrentTime().toSeconds();
        if (currentSec < activeEndSeconds) {
            setClipBounds(currentSec, activeEndSeconds);
        } else {
            showAlert("Marcação Inválida", "O início do corte deve ser menor que o término atual (%.1fs).".formatted(activeEndSeconds));
        }
    }

    @FXML
    private void onAdjustEndMinus1() {
        adjustClipBounds(0.0, -1.0);
    }

    @FXML
    private void onAdjustEndPlus1() {
        adjustClipBounds(0.0, 1.0);
    }

    @FXML
    private void onSetEndCurrent() {
        if (mediaPlayer == null || currentPreviewClip == null) {
            showAlert("Player Inativo", "Selecione e reproduza um corte para marcar a posição atual.");
            return;
        }
        if (isPlayingRenderedFile) {
            showAlert("Vídeo Já Renderizado", "Este corte já foi finalizado. O ajuste de marcadores aplica-se a vídeos antes da renderização.");
            return;
        }
        double currentSec = mediaPlayer.getCurrentTime().toSeconds();
        if (currentSec > activeStartSeconds) {
            setClipBounds(activeStartSeconds, currentSec);
        } else {
            showAlert("Marcação Inválida", "O término do corte deve ser maior que o início atual (%.1fs).".formatted(activeStartSeconds));
        }
    }

    private void adjustClipBounds(double deltaStart, double deltaEnd) {
        if (currentPreviewClip == null) {
            showAlert("Nenhum Corte", "Selecione um corte na tabela antes de ajustar os marcadores.");
            return;
        }
        if (isPlayingRenderedFile) {
            showAlert("Vídeo Já Renderizado", "Este corte já foi finalizado. O ajuste de marcadores aplica-se a vídeos antes da renderização.");
            return;
        }
        double newStart = Math.max(0, currentPreviewClip.startSeconds() + deltaStart);
        double newEnd = Math.max(newStart + 1.0, currentPreviewClip.endSeconds() + deltaEnd);
        setClipBounds(newStart, newEnd);
    }

    private void setClipBounds(double start, double end) {
        if (currentPreviewClip == null) return;
        this.activeStartSeconds = start;
        this.activeEndSeconds = end;

        ClipCandidate updated = currentPreviewClip.withBounds(start, end);
        currentPreviewClip = updated;

        lblStartSeconds.setText(String.format("%.1fs", start));
        lblEndSeconds.setText(String.format("%.1fs", end));

        // Atualiza na lista observável
        for (int i = 0; i < clipList.size(); i++) {
            if (clipList.get(i).id() != null && clipList.get(i).id().equals(updated.id())) {
                clipList.set(i, updated);
                break;
            }
        }

        // Se o player estiver ativo e tocando o vídeo fonte, faz seek para o novo início para feedback visual imediato
        if (mediaPlayer != null && !isPlayingRenderedFile) {
            mediaPlayer.seek(Duration.seconds(start));
            timeSlider.setValue(start);
        }

        // Persiste no banco de dados
        Thread.ofVirtual().start(() -> {
            db.updateClipTimeBounds(updated.id(), start, end);
        });

        logMessage("Marcadores atualizados: %.1fs -> %.1fs (Duração: %.1fs)".formatted(start, end, end - start), false);
    }

    @FXML
    private void onRenderSelectedClip() {
        ClipCandidate selected = clipTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Nenhum corte selecionado", "Selecione um corte na tabela antes de renderizar.");
            return;
        }

        VideoSource source = findSourceForClip(selected);
        if (source == null) {
            showAlert("Erro", "Vídeo de origem não encontrado para este corte.");
            return;
        }

        AppSettings settings = buildCurrentSettings();
        logMessage("Iniciando renderização de: " + selected.title(), false);
        pipelineService.renderClipsForSource(source, List.of(selected), settings);
    }

    private VideoSource findSourceForClip(ClipCandidate clip) {
        if (clip.videoSourceId() == null) {
            if (videoList.size() == 1) return videoList.getFirst();
            return null;
        }
        for (VideoSource vs : videoList) {
            if (clip.videoSourceId().equals(vs.id())) {
                return vs;
            }
        }
        if (videoList.size() == 1) return videoList.getFirst();
        return null;
    }

    private void loadSettings() {
        Thread.ofVirtual().start(() -> {
            AppSettings settings = db.getSettings();
            String currentKey = settings.geminiApiKey();
            GeminiApiKeyResolver.ResolutionResult autoResolution = null;

            if (currentKey == null || currentKey.isBlank()) {
                autoResolution = GeminiApiKeyResolver.resolveWithDetails();
                if (autoResolution.isFound()) {
                    currentKey = autoResolution.key();
                    AppSettings updated = new AppSettings(
                            settings.outputDirectory(),
                            settings.minViralityScore(),
                            settings.autoProcessAboveThreshold(),
                            settings.defaultOrientation(),
                            settings.autoVerticalMaxDurationSeconds(),
                            settings.burnSubtitles(),
                            settings.cpuThreads(),
                            currentKey,
                            settings.geminiModel()
                    );
                    db.saveSettings(updated);
                    logMessage("Chave GEMINI_API_KEY detectada automaticamente em [" + autoResolution.source() + "] e configurada.", false);
                }
            }

            final String finalKey = currentKey;
            final GeminiApiKeyResolver.ResolutionResult finalResolution = autoResolution;

            Platform.runLater(() -> {
                outputDirField.setText(settings.outputDirectory());
                geminiApiKeyField.setText(finalKey != null ? finalKey : "");
                chkSubtitles.setSelected(settings.burnSubtitles());
                chkAutoProcess.setSelected(settings.autoProcessAboveThreshold());
                comboOrientation.setValue(settings.defaultOrientation());

                if (geminiApiKeyStatusLabel != null) {
                    if (finalResolution != null && finalResolution.isFound()) {
                        geminiApiKeyStatusLabel.setText("Chave detectada automaticamente de: " + finalResolution.source());
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-size: 11px;");
                    } else if (finalKey != null && !finalKey.isBlank()) {
                        geminiApiKeyStatusLabel.setText("Chave configurada e pronta para uso.");
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-size: 11px;");
                    } else {
                        geminiApiKeyStatusLabel.setText("Nenhuma chave configurada. Cole-a acima ou clique em 'Buscar no Ambiente'.");
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
                    }
                }

                checkEnvironmentDiagnostics();
            });
        });
    }

    private void registerEventBusListeners() {
        eventBus.subscribe(ClipprEvent.LogMessage.class, event -> {
            if (!event.isDebug() || chkDebugMode.isSelected()) {
                logMessage(event.message(), event.isDebug());
            }
        });

        eventBus.subscribe(ClipprEvent.ProgressUpdated.class, event -> Platform.runLater(() -> {
            progressBar.setProgress(event.progress());
            statusLabel.setText(event.details());
        }));

        eventBus.subscribe(ClipprEvent.VideoSourceUpdated.class, event -> Platform.runLater(() -> {
            boolean found = false;
            for (int i = 0; i < videoList.size(); i++) {
                if (videoList.get(i).id() != null && videoList.get(i).id().equals(event.videoSource().id())) {
                    videoList.set(i, event.videoSource());
                    found = true;
                    break;
                }
            }
            if (!found) {
                videoList.add(event.videoSource());
            }
            updatePipelineButtonsState();
        }));

        eventBus.subscribe(ClipprEvent.ClipsEvaluated.class, event -> Platform.runLater(() -> {
            for (ClipCandidate approved : event.approvedClips()) {
                boolean exists = clipList.stream().anyMatch(c -> c.id() != null && c.id().equals(approved.id()));
                if (!exists) {
                    clipList.add(approved);
                }
            }
            if (!event.approvedClips().isEmpty()) {
                tabPane.getSelectionModel().select(1); // Muda para a aba de cortes
            }
            updatePipelineButtonsState();
        }));

        eventBus.subscribe(ClipprEvent.ClipRenderCompleted.class, event -> Platform.runLater(() -> {
            for (int i = 0; i < clipList.size(); i++) {
                if ((clipList.get(i).id() != null && clipList.get(i).id().equals(event.clip().id())) ||
                    clipList.get(i).title().equals(event.clip().title())) {
                    clipList.set(i, event.clip());
                    break;
                }
            }
            statusLabel.setText("Renderizado: " + event.clip().title());
            updatePipelineButtonsState();
        }));

        eventBus.subscribe(ClipprEvent.PipelineError.class, event -> Platform.runLater(() -> {
            statusLabel.setText("Erro: " + event.errorMessage());
            logMessage("[ERRO " + event.stage() + "] " + event.errorMessage(), false);
            updatePipelineButtonsState();
        }));
    }

    private void updatePipelineButtonsState() {
        boolean hasActive = videoList.stream().anyMatch(v ->
                v.status() == ProcessingStatus.FETCHING_TRANSCRIPT ||
                v.status() == ProcessingStatus.ANALYZING_AI ||
                v.status() == ProcessingStatus.DOWNLOADING_VIDEO ||
                v.status() == ProcessingStatus.CUTTING_FFMPEG
        );
        btnStartPipeline.setDisable(hasActive);
        btnStartPipeline.setText(hasActive ? "⏳ Processando Fila..." : "▶ Iniciar Processamento");
    }

    public void checkEnvironmentDiagnostics() {
        Thread.ofVirtual().start(() -> {
            boolean pgConnected = db.isConnected();
            FfmpegExecutor ffmpeg = new FfmpegExecutor();
            String ffmpegPath = ffmpeg.getFfmpegBinary();
            boolean ffmpegOk = Files.isExecutable(Path.of(ffmpegPath));

            YtDlpManager ytdlp = new YtDlpManager();
            Path ytdlpPath = ytdlp.getBinaryPath();
            boolean ytdlpOk = Files.isExecutable(ytdlpPath);

            String apiKey = geminiApiKeyField != null ? geminiApiKeyField.getText() : null;
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = db.getSettings().geminiApiKey();
            }
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = GeminiApiKeyResolver.resolveApiKey();
            }
            boolean geminiOk = apiKey != null && !apiKey.isBlank();
            String maskedKey = geminiOk ? (apiKey.length() > 8 ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "***") : "";

            Platform.runLater(() -> {
                if (pgConnected) {
                    dbStatusBadge.setText("● PostgreSQL: Conectado");
                    dbStatusBadge.setStyle("-fx-text-fill: -color-success-fg;");
                    diagPostgresLabel.setText("Conectado (localhost:5432/clippr_db)");
                } else {
                    dbStatusBadge.setText("● PostgreSQL: Desconectado");
                    dbStatusBadge.setStyle("-fx-text-fill: -color-warning-fg;");
                    diagPostgresLabel.setText("Desconectado (Inicie o Docker Compose)");
                }

                if (ffmpegOk) {
                    ffmpegStatusBadge.setText("● FFmpeg: OK");
                    ffmpegStatusBadge.setStyle("-fx-text-fill: -color-success-fg;");
                    diagFfmpegLabel.setText(ffmpegPath);
                } else {
                    ffmpegStatusBadge.setText("● FFmpeg: Não encontrado");
                    ffmpegStatusBadge.setStyle("-fx-text-fill: -color-danger-fg;");
                    diagFfmpegLabel.setText("Não encontrado no PATH");
                }

                if (geminiStatusBadge != null) {
                    if (geminiOk) {
                        geminiStatusBadge.setText("● Gemini: OK");
                        geminiStatusBadge.setStyle("-fx-text-fill: -color-success-fg;");
                    } else {
                        geminiStatusBadge.setText("● Gemini: Não configurado");
                        geminiStatusBadge.setStyle("-fx-text-fill: -color-warning-fg;");
                    }
                }

                if (diagGeminiLabel != null) {
                    if (geminiOk) {
                        diagGeminiLabel.setText("Configurada (" + maskedKey + ")");
                        diagGeminiLabel.setStyle("-fx-text-fill: -color-success-fg;");
                    } else {
                        diagGeminiLabel.setText("Não configurada");
                        diagGeminiLabel.setStyle("-fx-text-fill: -color-danger-fg;");
                    }
                }

                diagYtDlpLabel.setText(ytdlpOk ? ytdlpPath.toString() : "Não disponível");
            });
        });
    }

    @FXML
    private void onAddLink() {
        String url = linkInputField.getText();
        if (url == null || url.isBlank()) return;

        url = url.trim();

        if (!VideoSource.isValidYouTubeUrl(url)) {
            showAlert("Link Inválido", "O link fornecido não parece ser um link válido do YouTube.\n\nExemplos aceitos:\n- https://www.youtube.com/watch?v=...\n- https://youtu.be/...\n- https://www.youtube.com/shorts/...");
            return;
        }

        final String finalUrl = url;
        boolean alreadyInQueue = videoList.stream().anyMatch(v -> v.url().equalsIgnoreCase(finalUrl));
        if (alreadyInQueue) {
            showAlert("Link Duplicado", "Este vídeo já está presente na fila.");
            return;
        }

        VideoSource source = VideoSource.fromUrl(finalUrl);
        linkInputField.clear();
        logMessage("Adicionando à fila: " + finalUrl, false);

        Thread.ofVirtual().start(() -> {
            VideoSource saved = db.saveVideo(source);
            Platform.runLater(() -> {
                videoList.add(saved);
                statusLabel.setText("Vídeo adicionado: " + saved.videoId());
            });
        });
    }

    @FXML
    private void onClearQueue() {
        videoList.clear();
        clipList.clear();
        disposeMediaPlayer();
        playerPlaceholderLabel.setText("Selecione um corte na tabela para assistir");
        playerPlaceholderLabel.setVisible(true);
        progressBar.setProgress(0.0);
        statusLabel.setText("Fila limpa. Adicione novos vídeos.");
        logMessage("Fila de vídeos e lista de cortes limpas.", false);
        updatePipelineButtonsState();
    }

    @FXML
    private void onStartPipeline() {
        if (videoList.isEmpty()) {
            showAlert("Fila Vazia", "Adicione pelo menos um link do YouTube antes de iniciar.");
            return;
        }

        List<VideoSource> pendingVideos = videoList.stream()
                .filter(v -> v.status() == ProcessingStatus.PENDING || v.status() == ProcessingStatus.FAILED)
                .toList();

        if (pendingVideos.isEmpty()) {
            showAlert("Tudo Pronto", "Todos os vídeos da fila já foram processados.");
            return;
        }

        btnStartPipeline.setDisable(true);
        btnStartPipeline.setText("⏳ Processando Fila...");

        AppSettings currentSettings = buildCurrentSettings();
        Thread.ofVirtual().start(() -> {
            db.saveSettings(currentSettings);
        });

        logMessage("Iniciando processamento de " + pendingVideos.size() + " vídeo(s)...", false);
        for (VideoSource source : pendingVideos) {
            pipelineService.processVideoSource(source, currentSettings);
        }
    }

    @FXML
    private void onRenderAllApproved() {
        if (clipList.isEmpty()) {
            showAlert("Sem Cortes", "Não há cortes detectados para renderizar.");
            return;
        }

        List<ClipCandidate> toRender = clipList.stream()
                .filter(c -> c.status() != ProcessingStatus.COMPLETED)
                .toList();

        if (toRender.isEmpty()) {
            showAlert("Todos Renderizados", "Todos os cortes desta lista já foram renderizados com sucesso.");
            return;
        }

        AppSettings settings = buildCurrentSettings();
        logMessage("Iniciando renderização de " + toRender.size() + " corte(s) aprovado(s)...", false);

        for (VideoSource source : videoList) {
            List<ClipCandidate> clipsForSource = toRender.stream()
                    .filter(c -> c.videoSourceId() != null && c.videoSourceId().equals(source.id()))
                    .toList();

            if (!clipsForSource.isEmpty()) {
                pipelineService.renderClipsForSource(source, clipsForSource, settings);
            }
        }
    }

    @FXML
    private void onSaveApiKey() {
        String key = geminiApiKeyField.getText();
        AppSettings current = buildCurrentSettings();
        AppSettings updated = new AppSettings(
                current.outputDirectory(),
                current.minViralityScore(),
                current.autoProcessAboveThreshold(),
                current.defaultOrientation(),
                current.autoVerticalMaxDurationSeconds(),
                current.burnSubtitles(),
                current.cpuThreads(),
                key != null ? key.trim() : "",
                current.geminiModel()
        );
        Thread.ofVirtual().start(() -> {
            db.saveSettings(updated);
            Platform.runLater(() -> {
                logMessage("Chave do Gemini salva com sucesso.", false);
                if (geminiApiKeyStatusLabel != null) {
                    if (key != null && !key.isBlank()) {
                        geminiApiKeyStatusLabel.setText("Chave salva e configurada com sucesso.");
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-size: 11px;");
                    } else {
                        geminiApiKeyStatusLabel.setText("Nenhuma chave salva. Configure manualmente ou busque no ambiente.");
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
                    }
                }
                checkEnvironmentDiagnostics();
                showAlert("Configuração Salva", "Chave de API do Gemini salva com sucesso.");
            });
        });
    }

    @FXML
    private void onDetectApiKey() {
        Thread.ofVirtual().start(() -> {
            GeminiApiKeyResolver.ResolutionResult result = GeminiApiKeyResolver.resolveWithDetails();
            Platform.runLater(() -> {
                if (result.isFound()) {
                    geminiApiKeyField.setText(result.key());
                    AppSettings current = buildCurrentSettings();
                    AppSettings updated = new AppSettings(
                            current.outputDirectory(),
                            current.minViralityScore(),
                            current.autoProcessAboveThreshold(),
                            current.defaultOrientation(),
                            current.autoVerticalMaxDurationSeconds(),
                            current.burnSubtitles(),
                            current.cpuThreads(),
                            result.key(),
                            current.geminiModel()
                    );
                    Thread.ofVirtual().start(() -> db.saveSettings(updated));
                    if (geminiApiKeyStatusLabel != null) {
                        geminiApiKeyStatusLabel.setText("Chave detectada automaticamente de: " + result.source());
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-size: 11px;");
                    }
                    logMessage("Chave GEMINI_API_KEY detectada em [" + result.source() + "] e salva com sucesso.", false);
                    showAlert("Chave Detectada", "Chave GEMINI_API_KEY encontrada com sucesso em:\n" + result.source() + "\n\nA chave foi aplicada e salva nas configurações do programa.");
                } else {
                    if (geminiApiKeyStatusLabel != null) {
                        geminiApiKeyStatusLabel.setText("Nenhuma chave GEMINI_API_KEY encontrada no ambiente nem em arquivos (.zshrc, .bashrc, .env).");
                        geminiApiKeyStatusLabel.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-size: 11px;");
                    }
                    showAlert("Chave Não Encontrada", "Não foi possível encontrar a variável GEMINI_API_KEY nas variáveis de ambiente nem nos arquivos de configuração (~/.zshrc, ~/.bashrc, .env).");
                }
                checkEnvironmentDiagnostics();
            });
        });
    }

    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecionar Pasta para Exportação de Vídeos");
        File dir = chooser.showDialog(outputDirField.getScene().getWindow());
        if (dir != null) {
            outputDirField.setText(dir.getAbsolutePath());
            AppSettings updated = buildCurrentSettings();
            Thread.ofVirtual().start(() -> {
                db.saveSettings(updated);
            });
            logMessage("Pasta de destino alterada para: " + dir.getAbsolutePath(), false);
        }
    }

    @FXML
    private void onStartDockerCompose() {
        Thread.ofVirtual().start(() -> {
            try {
                Platform.runLater(() -> {
                    diagPostgresLabel.setText("Subindo container...");
                });
                logMessage("Executando 'docker compose up -d'...", false);
                Process p = new ProcessBuilder("docker", "compose", "up", "-d")
                        .redirectErrorStream(true)
                        .start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logMessage("[Docker] " + line, false);
                    }
                }
                int exit = p.waitFor();
                if (exit == 0) {
                    logMessage("Docker Compose executado. Aguardando conexão com PostgreSQL...", false);
                    boolean ready = false;
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(1000);
                        if (db.init() && db.isConnected()) {
                            ready = true;
                            break;
                        }
                    }
                    if (ready) {
                        logMessage("PostgreSQL pronto e conectado com sucesso!", false);
                    } else {
                        logMessage("PostgreSQL ainda inicializando. Verifique em instantes.", false);
                    }
                    checkEnvironmentDiagnostics();
                } else {
                    logMessage("Falha ao subir Docker Compose (exit code " + exit + ")", false);
                }
            } catch (Exception e) {
                logMessage("Erro ao iniciar Docker: " + e.getMessage(), false);
            }
        });
    }

    @FXML
    private void onClearLogs() {
        logArea.clear();
    }

    private AppSettings buildCurrentSettings() {
        return new AppSettings(
                outputDirField.getText() != null && !outputDirField.getText().isBlank() ? outputDirField.getText() : AppSettings.defaultSettings().outputDirectory(),
                90,
                chkAutoProcess.isSelected(),
                comboOrientation.getValue() != null ? comboOrientation.getValue() : OrientationMode.AUTO,
                60.0,
                chkSubtitles.isSelected(),
                0, // 100% CPU
                geminiApiKeyField.getText(),
                "gemini-3.8-flash"
        );
    }

    private void logMessage(String message, boolean isDebug) {
        Platform.runLater(() -> {
            String prefix = isDebug ? "[DEBUG] " : "";
            logArea.appendText(prefix + message + "\n");
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
