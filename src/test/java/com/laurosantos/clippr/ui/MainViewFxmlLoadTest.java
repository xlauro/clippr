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

    @Test
    @DisplayName("Verifica presença dos componentes de filtro de corte e colunas na interface FXML")
    void testClipFilterAndColumnsPresent() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<FXMLLoader> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/laurosantos/clippr/ui/main-view.fxml"));
                Parent root = loader.load();
                new javafx.scene.Scene(root);
                future.complete(loader);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        FXMLLoader loader = future.get(10, TimeUnit.SECONDS);
        MainController controller = loader.getController();

        javafx.scene.control.ComboBox<?> comboFilter = controller.getComboClipFilter();
        assertNotNull(comboFilter, "comboClipFilter deve ser injetado pelo FXML");
        org.junit.jupiter.api.Assertions.assertEquals(3, comboFilter.getItems().size(), "Deve conter 3 opções de filtro");
        org.junit.jupiter.api.Assertions.assertEquals("Todos os Cortes", comboFilter.getValue());

        javafx.scene.control.TableView<?> clipTable = controller.getClipTableView();
        assertNotNull(clipTable, "clipTableView deve ser injetado pelo FXML");

        boolean hasColClipType = clipTable.getColumns().stream()
                .anyMatch(c -> "colClipType".equals(c.getId()) || "Tipo".equals(c.getText()));
        org.junit.jupiter.api.Assertions.assertTrue(hasColClipType, "colClipType deve existir na tabela de cortes");
    }

    @Test
    @DisplayName("Garante que o filtro de Cortes Curtos e Longos funciona reativamente na tabela")
    void testClipFilteringFunctionality() throws ExecutionException, InterruptedException, TimeoutException {
        com.laurosantos.clippr.domain.ClipCandidate shortClip = new com.laurosantos.clippr.domain.ClipCandidate(
                1L, 100L, "Dica Rápida", "Gancho Curto", 10.0, 50.0, 95,
                "Raciocínio", "Legenda", java.util.List.of("tag1"),
                com.laurosantos.clippr.domain.OrientationMode.VERTICAL_9_16,
                true, null, null, com.laurosantos.clippr.domain.ProcessingStatus.PENDING
        );

        com.laurosantos.clippr.domain.ClipCandidate longClip = new com.laurosantos.clippr.domain.ClipCandidate(
                2L, 100L, "Entrevista Completa", "Gancho Longo", 60.0, 240.0, 88,
                "Raciocínio Longo", "Legenda Longa", java.util.List.of("tag2"),
                com.laurosantos.clippr.domain.OrientationMode.HORIZONTAL_16_9,
                false, null, null, com.laurosantos.clippr.domain.ProcessingStatus.PENDING
        );

        CompletableFuture<MainController> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/laurosantos/clippr/ui/main-view.fxml"));
                loader.load();
                MainController controller = loader.getController();
                controller.getClipList().addAll(shortClip, longClip);
                future.complete(controller);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        MainController controller = future.get(10, TimeUnit.SECONDS);

        CompletableFuture<Void> testStep = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                org.junit.jupiter.api.Assertions.assertEquals(2, controller.getClipTableView().getItems().size(),
                        "Deve conter 2 cortes com filtro 'Todos'");

                // 2. Filtro Apenas Curtos
                controller.getComboClipFilter().setValue("⚡ Apenas Curtos");
                org.junit.jupiter.api.Assertions.assertEquals(1, controller.getClipTableView().getItems().size(),
                        "Deve conter 1 corte curto");
                org.junit.jupiter.api.Assertions.assertEquals("Dica Rápida",
                        controller.getClipTableView().getItems().getFirst().title());

                // 3. Filtro Apenas Longos
                controller.getComboClipFilter().setValue("🎬 Apenas Longos");
                org.junit.jupiter.api.Assertions.assertEquals(1, controller.getClipTableView().getItems().size(),
                        "Deve conter 1 corte longo");
                org.junit.jupiter.api.Assertions.assertEquals("Entrevista Completa",
                        controller.getClipTableView().getItems().getFirst().title());

                // 4. Retornar a Todos
                controller.getComboClipFilter().setValue("Todos os Cortes");
                org.junit.jupiter.api.Assertions.assertEquals(2, controller.getClipTableView().getItems().size(),
                        "Deve retornar a 2 cortes");

                // 5. Testar valores das colunas colClipType e colClipTime
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<com.laurosantos.clippr.domain.ClipCandidate, String> colType =
                        (javafx.scene.control.TableColumn<com.laurosantos.clippr.domain.ClipCandidate, String>) controller.getClipTableView().getColumns().getFirst();
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<com.laurosantos.clippr.domain.ClipCandidate, String> colTime =
                        (javafx.scene.control.TableColumn<com.laurosantos.clippr.domain.ClipCandidate, String>) controller.getClipTableView().getColumns().stream()
                                .filter(c -> "colClipTime".equals(c.getId()))
                                .findFirst().orElseThrow();

                org.junit.jupiter.api.Assertions.assertEquals("⚡ Curto",
                        colType.getCellObservableValue(shortClip).getValue());
                org.junit.jupiter.api.Assertions.assertEquals("🎬 Longo",
                        colType.getCellObservableValue(longClip).getValue());
                org.junit.jupiter.api.Assertions.assertTrue(
                        colTime.getCellObservableValue(shortClip).getValue().contains("00:10 - 00:50"));
                org.junit.jupiter.api.Assertions.assertTrue(
                        colTime.getCellObservableValue(longClip).getValue().contains("01:00 - 04:00"));

                testStep.complete(null);
            } catch (Throwable t) {
                testStep.completeExceptionally(t);
            }
        });

        testStep.get(10, TimeUnit.SECONDS);
    }
}
