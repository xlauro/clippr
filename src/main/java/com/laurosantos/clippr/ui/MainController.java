package com.laurosantos.clippr.ui;

import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainController {

    public MainController() {}

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private Label dbStatusBadge;
    @FXML private Label ffmpegStatusBadge;
    @FXML private Label diagPostgresLabel;
    @FXML private Label diagFfmpegLabel;
    @FXML private Label diagYtDlpLabel;

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
    @FXML private MediaView mediaView;
    @FXML private Label playerPlaceholderLabel;
    @FXML private Slider timeSlider;
    @FXML private Label timeLabel;
    @FXML private Button btnPlayPause;
    @FXML private Slider volumeSlider;
    @FXML private Label lblStartSeconds;
    @FXML private Label lblEndSeconds;

    @FXML private PasswordField geminiApiKeyField;
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
    private boolean isSeeking = false;

    @FXML
    private void initialize() {
        setupTableColumns();
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

    private void setupOrientationComboBox() {
        comboOrientation.setItems(FXCollections.observableArrayList(OrientationMode.values()));
        comboOrientation.setValue(OrientationMode.AUTO);
    }

    private void setupPlayerSelectionListener() {
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
        lblStartSeconds.setText(String.format("%.1fs", clip.startSeconds()));
        lblEndSeconds.setText(String.format("%.1fs", clip.endSeconds()));

        disposeMediaPlayer();

        // 1. Tenta carregar o clipe já renderizado
        if (clip.outputFilePath() != null && Files.exists(Path.of(clip.outputFilePath()))) {
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
        }

        // 3. Vídeo fonte ainda não baixado
        playerPlaceholderLabel.setText("Vídeo original ainda não baixado.\nRenderize o corte para assistir.");
        playerPlaceholderLabel.setVisible(true);
    }

    private void playMediaFile(File videoFile, double startOffsetSeconds, double endLimitSeconds) {
        try {
            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
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

                    // Se for pré-visualização de trecho não cortado, para no final do corte
                    if (endLimitSeconds > startOffsetSeconds && currentSec >= endLimitSeconds) {
                        mediaPlayer.pause();
                        mediaPlayer.seek(Duration.seconds(startOffsetSeconds));
                        btnPlayPause.setText("▶ Play");
                    }
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                btnPlayPause.setText("▶ Play");
                mediaPlayer.seek(Duration.seconds(startOffsetSeconds));
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
        if (mediaPlayer != null && currentPreviewClip != null) {
            double currentSec = mediaPlayer.getCurrentTime().toSeconds();
            if (currentSec < currentPreviewClip.endSeconds()) {
                setClipBounds(currentSec, currentPreviewClip.endSeconds());
            }
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
        if (mediaPlayer != null && currentPreviewClip != null) {
            double currentSec = mediaPlayer.getCurrentTime().toSeconds();
            if (currentSec > currentPreviewClip.startSeconds()) {
                setClipBounds(currentPreviewClip.startSeconds(), currentSec);
            }
        }
    }

    private void adjustClipBounds(double deltaStart, double deltaEnd) {
        if (currentPreviewClip == null) return;
        double newStart = Math.max(0, currentPreviewClip.startSeconds() + deltaStart);
        double newEnd = Math.max(newStart + 1.0, currentPreviewClip.endSeconds() + deltaEnd);
        setClipBounds(newStart, newEnd);
    }

    private void setClipBounds(double start, double end) {
        if (currentPreviewClip == null) return;
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

        // Persiste no banco de dados
        Thread.ofVirtual().start(() -> {
            db.updateClipTimeBounds(updated.id(), start, end);
        });

        logMessage("Marcadores de tempo atualizados: %.1fs -> %.1fs".formatted(start, end), false);
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
        logMessage("Renderizando corte selecionado: " + selected.title(), false);
        pipelineService.renderClipsForSource(source, List.of(selected), settings);
    }

    private VideoSource findSourceForClip(ClipCandidate clip) {
        if (clip.videoSourceId() == null) return null;
        for (VideoSource vs : videoList) {
            if (clip.videoSourceId().equals(vs.id())) {
                return vs;
            }
        }
        return null;
    }

    private void loadSettings() {
        Thread.ofVirtual().start(() -> {
            AppSettings settings = db.getSettings();
            Platform.runLater(() -> {
                outputDirField.setText(settings.outputDirectory());
                geminiApiKeyField.setText(settings.geminiApiKey());
                chkSubtitles.setSelected(settings.burnSubtitles());
                chkAutoProcess.setSelected(settings.autoProcessAboveThreshold());
                comboOrientation.setValue(settings.defaultOrientation());
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
            for (int i = 0; i < videoList.size(); i++) {
                if (videoList.get(i).id() != null && videoList.get(i).id().equals(event.videoSource().id())) {
                    videoList.set(i, event.videoSource());
                    return;
                }
            }
        }));

        eventBus.subscribe(ClipprEvent.ClipsEvaluated.class, event -> Platform.runLater(() -> {
            clipList.addAll(event.approvedClips());
            if (!event.approvedClips().isEmpty()) {
                tabPane.getSelectionModel().select(1); // Muda para a aba de cortes
            }
        }));

        eventBus.subscribe(ClipprEvent.ClipRenderCompleted.class, event -> Platform.runLater(() -> {
            for (int i = 0; i < clipList.size(); i++) {
                if (clipList.get(i).title().equals(event.clip().title())) {
                    clipList.set(i, event.clip());
                    break;
                }
            }
            statusLabel.setText("Renderizado: " + event.clip().title());
        }));

        eventBus.subscribe(ClipprEvent.PipelineError.class, event -> Platform.runLater(() -> {
            statusLabel.setText("Erro: " + event.errorMessage());
            logMessage("[ERRO " + event.stage() + "] " + event.errorMessage(), false);
        }));
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

                diagYtDlpLabel.setText(ytdlpOk ? ytdlpPath.toString() : "Não disponível");
            });
        });
    }

    @FXML
    private void onAddLink() {
        String url = linkInputField.getText();
        if (url == null || url.isBlank()) return;

        url = url.trim();
        VideoSource source = VideoSource.fromUrl(url);
        VideoSource saved = db.saveVideo(source);
        videoList.add(saved);
        linkInputField.clear();
        logMessage("Link adicionado à fila: " + url, false);
    }

    @FXML
    private void onClearQueue() {
        videoList.clear();
        logMessage("Fila de vídeos limpa.", false);
    }

    @FXML
    private void onStartPipeline() {
        if (videoList.isEmpty()) {
            showAlert("Fila Vazia", "Adicione pelo menos um link do YouTube antes de iniciar.");
            return;
        }

        AppSettings currentSettings = buildCurrentSettings();
        db.saveSettings(currentSettings);

        logMessage("Iniciando processamento em lote da fila de vídeos...", false);
        for (VideoSource source : videoList) {
            pipelineService.processVideoSource(source, currentSettings);
        }
    }

    @FXML
    private void onRenderAllApproved() {
        if (clipList.isEmpty()) {
            showAlert("Sem Cortes", "Não há cortes detectados para renderizar.");
            return;
        }

        AppSettings settings = buildCurrentSettings();
        logMessage("Iniciando renderização de todos os cortes aprovados...", false);

        for (VideoSource source : videoList) {
            List<ClipCandidate> clipsForSource = clipList.stream()
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
        AppSettings updated = new AppSettings(
                outputDirField.getText(),
                90,
                chkAutoProcess.isSelected(),
                comboOrientation.getValue(),
                60.0,
                chkSubtitles.isSelected(),
                0,
                key,
                "gemini-2.5-flash"
        );
        db.saveSettings(updated);
        logMessage("Chave do Gemini salva com sucesso.", false);
        showAlert("Configuração Salva", "Chave de API do Gemini atualizada com sucesso.");
    }

    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecionar Pasta para Exportação de Vídeos");
        File dir = chooser.showDialog(outputDirField.getScene().getWindow());
        if (dir != null) {
            outputDirField.setText(dir.getAbsolutePath());
            AppSettings updated = buildCurrentSettings();
            db.saveSettings(updated);
            logMessage("Pasta de destino alterada para: " + dir.getAbsolutePath(), false);
        }
    }

    @FXML
    private void onStartDockerCompose() {
        Thread.ofVirtual().start(() -> {
            try {
                logMessage("Executando 'docker compose up -d'...", false);
                Process p = new ProcessBuilder("docker", "compose", "up", "-d").start();
                int exit = p.waitFor();
                if (exit == 0) {
                    logMessage("Docker Compose iniciado com sucesso.", false);
                    Thread.sleep(1500);
                    db.init();
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
                "gemini-2.5-flash"
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
