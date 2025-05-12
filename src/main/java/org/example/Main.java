package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.example.controller.FullScreenController;

import org.example.service.AllServices;
import org.example.service.ServicesException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Objects;

public class Main extends Application {
    private static final Log log = LogFactory.getLog(Main.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            AllServices allServices = getService();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui_thing/views/FullScreenView.fxml"));

            Parent root = loader.load();
            FullScreenController ctrl = loader.getController();
            ctrl.setServices(allServices);

            // Create scene with FIXED dimensions instead of automatic sizing
            primaryStage.setScene(new Scene(root, 1280, 800));

            primaryStage.setTitle("GeneExplorer - Drug Repurposing");
            Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/ui_thing/logoBrand.png")));
            primaryStage.getIcons().add(icon);

            primaryStage.setMaximized(false);
            primaryStage.centerOnScreen();

            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(768);

            // Make window standalone (not decorated by browser)
            primaryStage.initStyle(StageStyle.DECORATED);
            primaryStage.show();

        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                t.printStackTrace();
                t = t.getCause();
            }
            new Alert(Alert.AlertType.ERROR,
                    "Startup failed—see console for details")
                    .showAndWait();
        }
    }
    public static void main(String[] args) {
        launch(args);
    }

    static AllServices getService() throws ServicesException {
        ApplicationContext context = new AnnotationConfigApplicationContext(GeneExplorerConfig.class);
        return context.getBean(AllServices.class);
    }
}