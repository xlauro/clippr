package com.laurosantos.clippr.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

import java.io.File;

public class MainController {

    @FXML
    private Label statusLabel;

    @FXML
    private Label selectedFileLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private TextArea logArea;

    @FXML
    private Button selectFileButton;

    @FXML
    private Button processButton;

    @FXML
    private void initialize() {
        statusLabel.setText("Pronto para carregar vídeo");
        logArea.appendText("Sistema inicializado com sucesso (Java 25 + JavaFX + AtlantaFX).\n");
    }

    @FXML
    private void onSelectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Vídeo para Edição");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vídeos (*.mp4, *.mov, *.mkv)", "*.mp4", "*.mov", "*.mkv"),
                new FileChooser.ExtensionFilter("Todos os Arquivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(selectFileButton.getScene().getWindow());
        if (file != null) {
            selectedFileLabel.setText(file.getName());
            statusLabel.setText("Arquivo selecionado: " + file.getName());
            logArea.appendText("Vídeo carregado: " + file.getAbsolutePath() + "\n");
        }
    }

    @FXML
    private void onProcessVideo() {
        statusLabel.setText("Processamento agendado...");
        logArea.appendText("Aguardando pipeline FFmpeg + IA para o corte...\n");
    }
}
