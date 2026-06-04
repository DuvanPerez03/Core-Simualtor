package com.coresimulator;

import com.coresimulator.controller.MainController;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Punto de entrada de la aplicación JavaFX. */
public class MainApp extends Application {

    /**
     * Inicializa y muestra la ventana principal.
     *
     * @param primaryStage ventana primaria provista por JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        MainController controller = new MainController();

        // Tamaño de la pantalla disponible (sin barra de tareas)
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        // Usar el 85% de la pantalla, con mínimos razonables
        double w = Math.max(1000, screen.getWidth()  * 0.85);
        double h = Math.max(660,  screen.getHeight() * 0.85);

        Scene scene = new Scene(controller.getRoot(), w, h);

        String cssPath = getClass()
                .getResource("/com/coresimulator/css/dark-theme.css")
                .toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("Simulador de Sistema Operativo");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);

        // Centrar en pantalla
        primaryStage.setX(screen.getMinX() + (screen.getWidth()  - w) / 2);
        primaryStage.setY(screen.getMinY() + (screen.getHeight() - h) / 2);

        primaryStage.show();
    }

    /** Lanza la aplicación JavaFX. */
    public static void main(String[] args) {
        launch(args);
    }
}
