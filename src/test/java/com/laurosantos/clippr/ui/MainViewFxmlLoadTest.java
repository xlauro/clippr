package com.laurosantos.clippr.ui;

import com.laurosantos.clippr.App;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MainViewFxmlLoadTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit já iniciado por outro teste
        }
    }

    @Test
    @DisplayName("Garante que o arquivo main-view.fxml carrega com sucesso e sem exceções de tipo ou binding")
    void testMainViewFxmlLoadsSuccessfully() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<FXMLLoader> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/laurosantos/clippr/ui/main-view.fxml"));
                Parent root = loader.load();
                new javafx.scene.Scene(root);
                javafx.scene.control.TabPane tabPane = (javafx.scene.control.TabPane) root.lookup("#tabPane");
                if (tabPane != null) {
                    tabPane.getSelectionModel().select(1);
                }
                future.complete(loader);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        FXMLLoader loader = future.get(10, TimeUnit.SECONDS);
        Parent root = loader.getRoot();
        assertNotNull(root, "A raiz da interface FXML deve ser carregada corretamente");
        assertNotNull(loader.getController(), "MainController deve ser instanciado");

        javafx.scene.control.TableView<?> videoTable = (javafx.scene.control.TableView<?>) root.lookup("#videoTableView");
        assertNotNull(videoTable, "videoTableView deve existir");
        assertNotNull(videoTable.getContextMenu(), "videoTableView deve possuir ContextMenu configurado");

        javafx.scene.control.Button btnStart = (javafx.scene.control.Button) root.lookup("#btnStartPipeline");
        assertNotNull(btnStart, "btnStartPipeline deve existir");
        org.junit.jupiter.api.Assertions.assertFalse(btnStart.isDisable(), "btnStartPipeline deve iniciar habilitado");
    }
}
