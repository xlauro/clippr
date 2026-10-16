package com.laurosantos.clippr;

import atlantafx.base.theme.PrimerDark;
import com.laurosantos.clippr.persistence.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    public App() {}

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/laurosantos/clippr/ui/main-view.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 1150, 750);
        stage.setTitle("Clippr - AI & FFmpeg Video Clipper");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        stage.setOnCloseRequest(_ -> {
            DatabaseManager.getInstance().close();
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
