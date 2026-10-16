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
        CompletableFuture<Parent> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/laurosantos/clippr/ui/main-view.fxml"));
                Parent root = loader.load();
                future.complete(root);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        Parent root = future.get(10, TimeUnit.SECONDS);
        assertNotNull(root, "A raiz da interface FXML deve ser carregada corretamente");
    }
}
