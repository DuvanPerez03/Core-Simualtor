package com.coresimulator.view;

import com.coresimulator.service.SimulatorFacade;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * Dashboard / Pantalla de inicio del simulador.
 * Capa: View (Presentación)
 */
public class DashboardView extends BorderPane {

    private final SimulatorFacade facade;
    private final Consumer<String> navigateTo;

    /**
     * Construye la pantalla principal (dashboard) del simulador.
     *
     * @param facade fachada de servicios del simulador
     * @param navigateTo función para navegar entre vistas
     */
    public DashboardView(SimulatorFacade facade, Consumer<String> navigateTo) {
        this.facade = facade;
        this.navigateTo = navigateTo;
        build();
    }

    private void build() {
        setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(28);
        content.setPadding(new Insets(0));

        // ── Hero ──────────────────────────────────────────────────
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(8, 0, 4, 0));

        Label title = new Label("Simulador de Sistema Operativo");
        title.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9; -fx-alignment: CENTER;");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        hero.getChildren().addAll(title);

        // ── Quick start ───────────────────────────────────────────
        HBox quickStart = new HBox(12);
        quickStart.setAlignment(Pos.CENTER);

        Button btnSample = new Button("Cargar Procesos de Ejemplo");
        btnSample.getStyleClass().add("btn-primary");
        btnSample.setOnAction(e -> {
            facade.loadSampleProcesses();
            navigateTo.accept("processes");
        });

        Button btnStart = new Button("▶  Ir a Planificador");
        btnStart.getStyleClass().add("btn-secondary");
        btnStart.setOnAction(e -> navigateTo.accept("scheduler"));

        quickStart.getChildren().addAll(btnSample, btnStart);

        // ── Feature cards (sin botón Explorar) ───────────────────
        HBox features = new HBox(12);

        VBox c1 = buildFeatureCard("⚙", "Planificador",
                "Round Robin · SJF · Prioridad",
                "Diagrama de Gantt en tiempo real con métricas de tiempo de espera y retorno.",
                "#00d4ff", "scheduler");

        VBox c2 = buildFeatureCard("🗄", "Memoria",
                "Paginación por Demanda",
                "Marcos de página con reemplazo FIFO y LRU. Visualización de page faults.",
                "#7c3aed", "memory");

        VBox c3 = buildFeatureCard("📁", "Archivos",
                "Acceso Concurrente · Mutex",
                "Simulación de lectura/escritura con semáforos y detección de conflictos.",
                "#10b981", "files");

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        features.getChildren().addAll(c1, c2, c3);

        // ── Sección inferior: Métricas de ejemplo ─────────────────
        VBox metricsCard = buildMetricsSection();

        content.getChildren().addAll(hero, quickStart, features, metricsCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildFeatureCard(String icon, String title, String subtitle, String desc, String color, String target) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));
        card.setStyle(card.getStyle() + "; -fx-cursor: hand;");

        // Hacer toda la card clickeable
        card.setOnMouseClicked(e -> navigateTo.accept(target));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 28px;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + "88; -fx-font-weight: bold;");

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        descLbl.setWrapText(true);

        card.getChildren().addAll(iconLbl, titleLbl, subLbl, descLbl);

        card.setOnMouseEntered(e ->
            card.setStyle("-fx-background-color: #1a2030; -fx-background-radius: 12; " +
                    "-fx-border-color: " + color + "44; -fx-border-radius: 12; -fx-border-width: 1; " +
                    "-fx-padding: 20; -fx-cursor: hand;"));
        card.setOnMouseExited(e ->
            card.setStyle("-fx-background-color: #141820; -fx-background-radius: 12; " +
                    "-fx-border-color: #1e2d3d; -fx-border-radius: 12; -fx-border-width: 1; " +
                    "-fx-padding: 20; -fx-cursor: hand;"));

        return card;
    }

    /** Sección de métricas / atajos de lo que el simulador puede medir */
    private VBox buildMetricsSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label sectionTitle = new Label("📊  ¿Qué métricas puedes observar?");
        sectionTitle.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px; -fx-font-weight: bold;");

        HBox grid = new HBox(12);

        grid.getChildren().addAll(
            buildMetricTile("⏱", "Tiempo de espera", "Cuánto espera cada proceso en la cola de listos.", "#00d4ff"),
            buildMetricTile("🔄", "Turnaround", "Tiempo total desde llegada hasta finalización del proceso.", "#7c3aed"),
            buildMetricTile("💥", "Page Faults", "Accesos a páginas que no estaban cargadas en memoria.", "#ef4444"),
            buildMetricTile("🔒", "Conflictos de archivo", "Bloqueos y reintentos en acceso concurrente a archivos.", "#f59e0b")
        );

        for (javafx.scene.Node n : grid.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        card.getChildren().addAll(sectionTitle, grid);
        return card;
    }

    private VBox buildMetricTile(String icon, String title, String desc, String color) {
        VBox tile = new VBox(6);
        tile.setPadding(new Insets(14));
        tile.setStyle("-fx-background-color: " + color + "0d; -fx-border-color: " + color + "33; " +
                "-fx-border-radius: 8; -fx-background-radius: 8;");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20px;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        descLbl.setWrapText(true);

        tile.getChildren().addAll(iconLbl, titleLbl, descLbl);
        return tile;
    }
}
