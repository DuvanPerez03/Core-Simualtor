package com.coresimulator.view;

import com.coresimulator.model.SchedulerResult;
import com.coresimulator.model.SchedulerResult.GanttEntry;
import com.coresimulator.model.Process;
import com.coresimulator.service.SimulatorFacade;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.*;

/**
 * Vista del planificador de procesos.
 *
 * Diagrama de Gantt animado bloque a bloque:
 *  - Cada bloque aparece uno a uno con el indicador amarillo
 *  - Round Robin: línea punteada entre bloques del mismo proceso (= divisor de quantum)
 *  - SJF / Prioridad: borde sólido, sin divisores
 *  - Leyenda de colores + info de quantum
 */
public class SchedulerView extends BorderPane {

    private static final Color[] PROCESS_COLORS = {
        Color.web("#00d4ff"), Color.web("#7c3aed"), Color.web("#10b981"),
        Color.web("#f59e0b"), Color.web("#ef4444"), Color.web("#ec4899"),
        Color.web("#8b5cf6"), Color.web("#06b6d4"), Color.web("#84cc16"),
        Color.web("#fb923c")
    };

    private final SimulatorFacade facade;

    // Controls
    private ComboBox<String> algoCombo;
    private Spinner<Integer> quantumSpinner;
    private HBox quantumBox;
    private Slider speedSlider;
    private Button btnRun, btnStop;

    // Gantt
    private Canvas ganttCanvas;
    private ScrollPane ganttScroll;
    private Label statusLabel;

    // Legend (referencia directa, sin buscar en el árbol)
    private FlowPane legendBar;

    // Metrics & table
    private HBox metricsBox;
    private TableView<Process> resultsTable;

    // Animation state
    private Timeline animation;
    private List<GanttEntry> ganttEntries;
    private Map<Integer, Color> colorMap;
    private int animStep;
    private double cellW, xOff, yBar, barH, canvasW;
    private int quantum;
    private boolean isRR;

    /**
     * Crea la vista del planificador.
     *
     * @param facade fachada de servicios del simulador
     */
    public SchedulerView(SimulatorFacade facade) {
        this.facade = facade;
        build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BUILD
    // ─────────────────────────────────────────────────────────────────────────

    private void build() {
        setStyle("-fx-background-color: transparent;");

        VBox header = new VBox(4);
        Label title = new Label("⚙  Planificación de Procesos");
        title.getStyleClass().add("page-title");
        Label sub = new Label(
            "Simula Round Robin, SJF y Prioridad · Gantt animado · " +
            "Líneas punteadas = divisores de quantum (RR).");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16, 0, 0, 0));

        legendBar = new FlowPane();

        content.getChildren().addAll(
            buildControls(),
            buildGanttCard(),
            legendBar,
            buildMetricsRow(),
            buildResultsCard()
        );

        VBox main = new VBox(0, header, content);

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONTROLES — dos filas
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildControls() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));

        // Fila 1: algoritmo + quantum
        HBox row1 = new HBox(16);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label algoLabel = new Label("Algoritmo:");
        algoLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        algoCombo = new ComboBox<>();
        algoCombo.getItems().addAll("Round Robin", "SJF (Shortest Job First)", "Prioridad");
        algoCombo.setValue("Round Robin");
        algoCombo.setPrefWidth(230);
        algoCombo.setStyle("-fx-background-color: #0d0f14; -fx-text-fill: #e2e8f0; " +
                "-fx-border-color: #2d3748; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label qLabel = new Label("Quantum (RR):");
        qLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        quantumSpinner = new Spinner<>(1, 10, facade.getRrQuantum());
        quantumSpinner.setEditable(true);
        quantumSpinner.setPrefWidth(80);

        quantumBox = new HBox(8, qLabel, quantumSpinner);
        quantumBox.setAlignment(Pos.CENTER_LEFT);

        algoCombo.setOnAction(e -> {
            boolean rr = algoCombo.getValue().startsWith("Round");
            quantumBox.setVisible(rr);
            quantumBox.setManaged(rr);
        });

        row1.getChildren().addAll(algoLabel, algoCombo, quantumBox);

        // Fila 2: velocidad + botones
        HBox row2 = new HBox(16);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label speedLabel = new Label("Velocidad de animación:");
        speedLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        // lento←slider→rápido: internamente delay = 1280 - valor
        speedSlider = new Slider(80, 1200, 500);
        speedSlider.setPrefWidth(160);

        Label lento  = new Label("Lento");
        Label rapido = new Label("Rápido");
        lento.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
        rapido.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");

        HBox sliderBox = new HBox(6, lento, speedSlider, rapido);
        sliderBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnStop = new Button("⏹  Detener");
        btnStop.getStyleClass().add("btn-secondary");
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> stopAnimation());

        btnRun = new Button("▶  Simular");
        btnRun.getStyleClass().add("btn-primary");
        btnRun.setOnAction(e -> startSimulation());

        row2.getChildren().addAll(speedLabel, sliderBox, spacer, btnStop, btnRun);

        card.getChildren().addAll(row1, new Separator(), row2);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GANTT CARD
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildGanttCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label ganttTitle = new Label("📊  Diagrama de Gantt");
        ganttTitle.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        statusLabel = new Label("Ejecuta una simulación para ver el diagrama.");
        statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        ganttCanvas = new Canvas(800, 140);
        ganttScroll = new ScrollPane(ganttCanvas);
        ganttScroll.setFitToHeight(false);
        ganttScroll.setFitToWidth(false);
        ganttScroll.setStyle("-fx-background-color: #0a0c10; -fx-background: #0a0c10;");
        ganttScroll.setPrefHeight(178);

        card.getChildren().addAll(ganttTitle, statusLabel, ganttScroll);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LEYENDA — se llena después de simular
    // ─────────────────────────────────────────────────────────────────────────

    private void updateLegend() {
        legendBar.getChildren().clear();
        legendBar.setHgap(12);
        legendBar.setVgap(8);
        legendBar.getStyleClass().add("card");
        legendBar.setPadding(new Insets(10, 16, 10, 16));

        Label lbl = new Label("Procesos:");
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        legendBar.getChildren().add(lbl);

        for (Map.Entry<Integer, Color> entry : colorMap.entrySet()) {
            int pid = entry.getKey();
            Color c = entry.getValue();

            String name = facade.getProcessList().stream()
                    .filter(p -> p.getPid() == pid)
                    .map(p -> "P" + pid + " · " + p.getName())
                    .findFirst().orElse("P" + pid);

            HBox item = new HBox(6);
            item.setAlignment(Pos.CENTER_LEFT);

            javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(14, 14);
            rect.setFill(c.deriveColor(0, 1, 1, 0.35));
            rect.setStroke(c);
            rect.setStrokeWidth(1.5);
            rect.setArcWidth(3);
            rect.setArcHeight(3);

            Label nameLbl = new Label(name);
            nameLbl.setStyle("-fx-text-fill: " + toHex(c) + "; -fx-font-size: 12px;");

            item.getChildren().addAll(rect, nameLbl);
            legendBar.getChildren().add(item);
        }

        // Info de quantum al final, en nueva fila si no cabe
        if (isRR) {
            Label qInfo = new Label(
                "⏱ Quantum = " + quantum +
                "   ┊ línea punteada = divisor de quantum" +
                "   | línea sólida = cambio de proceso");
            qInfo.setStyle("-fx-text-fill: #4a5568; -fx-font-size: 11px;");
            legendBar.getChildren().add(qInfo);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MÉTRICAS
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildMetricsRow() {
        metricsBox = new HBox(12);
        metricsBox.setAlignment(Pos.CENTER_LEFT);
        return metricsBox;
    }

    private void updateMetrics(SchedulerResult r) {
        metricsBox.getChildren().clear();
        metricsBox.getChildren().addAll(
            buildMetricCard("⏱ T. Espera Prom.",  String.format("%.2f", r.getAvgWaitingTime()),     "#00d4ff"),
            buildMetricCard("🔄 T. Retorno Prom.", String.format("%.2f", r.getAvgTurnaroundTime()), "#7c3aed"),
            buildMetricCard("💻 Uso de CPU",        String.format("%.1f%%", r.getCpuUtilization()),  "#10b981"),
            buildMetricCard("✅ Procesos",          String.valueOf(r.getCompletedProcesses().size()), "#f59e0b")
        );
        for (javafx.scene.Node n : metricsBox.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }
    }

    private VBox buildMetricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        card.getChildren().addAll(val, lbl);
        return card;
    }

    private VBox buildResultsCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label resTitle = new Label("📋  Métricas por Proceso");
        resTitle.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        resultsTable = new TableView<>();
        resultsTable.getStyleClass().add("table-view");
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        resultsTable.setPrefHeight(200);
        resultsTable.setPlaceholder(new Label("Sin resultados aún."));

        TableColumn<Process, Integer> c1 = new TableColumn<>("PID");
        c1.setCellValueFactory(new PropertyValueFactory<>("pid"));
        c1.setMaxWidth(50);

        TableColumn<Process, String> c2 = new TableColumn<>("Proceso");
        c2.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Process, Integer> c3 = new TableColumn<>("Duración");
        c3.setCellValueFactory(new PropertyValueFactory<>("burstTime"));

        TableColumn<Process, Integer> c4 = new TableColumn<>("T. Espera");
        c4.setCellValueFactory(new PropertyValueFactory<>("waitingTime"));

        TableColumn<Process, Integer> c5 = new TableColumn<>("T. Retorno");
        c5.setCellValueFactory(new PropertyValueFactory<>("turnaroundTime"));

        resultsTable.getColumns().addAll(c1, c2, c3, c4, c5);
        card.getChildren().addAll(resTitle, resultsTable);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SIMULACIÓN Y ANIMACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private void startSimulation() {
        if (facade.getProcessList().isEmpty()) {
            statusLabel.setText("⚠  No hay procesos. Ve a 'Procesos' y agrega algunos primero.");
            statusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px;");
            return;
        }

        stopAnimation();

        quantum = quantumSpinner.getValue();
        facade.setRrQuantum(quantum);
        isRR = algoCombo.getValue().startsWith("Round");

        SchedulerResult result = switch (algoCombo.getValue()) {
            case "SJF (Shortest Job First)" -> facade.runSJF();
            case "Prioridad"                -> facade.runPriority();
            default                         -> facade.runRoundRobin();
        };

        ganttEntries = result.getGanttChart();
        if (ganttEntries.isEmpty()) return;

        // Mapa de colores por PID
        colorMap = new LinkedHashMap<>();
        ganttEntries.stream()
                .filter(e -> e.pid() != -1)
                .map(GanttEntry::pid)
                .distinct()
                .forEach(pid -> colorMap.put(pid,
                        PROCESS_COLORS[colorMap.size() % PROCESS_COLORS.length]));

        updateLegend();

        // Dimensiones del canvas
        int totalTime = ganttEntries.stream().mapToInt(GanttEntry::endTime).max().orElse(1);
        cellW   = Math.max(44, 680.0 / totalTime);
        canvasW = Math.max(800, totalTime * cellW + 80);
        xOff    = 30;
        yBar    = 24;
        barH    = 54;

        ganttCanvas.setWidth(canvasW);
        ganttCanvas.setHeight(150);

        // Canvas limpio
        GraphicsContext gc = ganttCanvas.getGraphicsContext2D();
        clearCanvas(gc);
        drawTimeAxis(gc, totalTime);

        // Métricas y tabla inmediatamente
        resultsTable.getItems().setAll(result.getCompletedProcesses());
        updateMetrics(result);

        animStep = 0;
        btnRun.setDisable(true);
        btnStop.setDisable(false);
        statusLabel.setText("▶  Animando " + algoCombo.getValue() + "...");
        statusLabel.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 12px;");

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (animation != null && animStep < ganttEntries.size()) {
                animation.stop();
                double nd = 1280.0 - newVal.doubleValue();
                animation = new Timeline(new KeyFrame(Duration.millis(nd), e -> animateStep(gc)));
                animation.setCycleCount(ganttEntries.size() - animStep);
                animation.setOnFinished(e -> onAnimationFinished(gc));
                animation.play();
            }
        });

        double delayMs = 1280.0 - speedSlider.getValue();
        animation = new Timeline(new KeyFrame(Duration.millis(delayMs), e -> animateStep(gc)));
        animation.setCycleCount(ganttEntries.size());
        animation.setOnFinished(e -> onAnimationFinished(gc));
        animation.play();
    }

    /**
     * Dibuja un bloque y luego (si es RR) el divisor entre este bloque
     * y el anterior si ambos pertenecen al mismo proceso.
     */
    private void animateStep(GraphicsContext gc) {
        if (animStep >= ganttEntries.size()) return;

        // Borrar indicador del paso anterior
        if (animStep > 0) {
            GanttEntry prev = ganttEntries.get(animStep - 1);
            // Redibujar sin indicador
            redrawBlock(gc, prev);
            // Redibujar divisores del bloque anterior
            if (isRR) drawQuantumDivider(gc, animStep - 1);
        }

        GanttEntry entry = ganttEntries.get(animStep);
        animStep++;

        drawBlock(gc, entry);

        // Divisor de quantum RR: línea punteada entre bloques consecutivos del mismo proceso
        if (isRR) drawQuantumDivider(gc, animStep - 1);

        // Ticks de tiempo
        drawTickLabel(gc, entry.startTime());
        if (animStep == ganttEntries.size()) drawTickLabel(gc, entry.endTime());

        // Indicador del bloque actual
        highlightCurrent(gc, entry);
    }

    private void onAnimationFinished(GraphicsContext gc) {
        // Limpiar indicador del último bloque
        if (!ganttEntries.isEmpty()) {
            GanttEntry last = ganttEntries.get(ganttEntries.size() - 1);
            redrawBlock(gc, last);
            if (isRR) drawQuantumDivider(gc, ganttEntries.size() - 1);
            drawTickLabel(gc, last.endTime());
        }
        btnRun.setDisable(false);
        btnStop.setDisable(true);
        animation = null;
        statusLabel.setText("✓  Completado · " + algoCombo.getValue()
            + (isRR ? "  (quantum = " + quantum + ")" : ""));
        statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px;");
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
        btnRun.setDisable(false);
        btnStop.setDisable(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DIBUJO
    // ─────────────────────────────────────────────────────────────────────────

    private void clearCanvas(GraphicsContext gc) {
        gc.setFill(Color.web("#0a0c10"));
        gc.fillRect(0, 0, canvasW, 160);
    }

    private void drawTimeAxis(GraphicsContext gc, int totalTime) {
        gc.setStroke(Color.web("#1e2d3d"));
        gc.setLineWidth(0.5);
        gc.strokeLine(xOff, yBar + barH + 1, xOff + totalTime * cellW, yBar + barH + 1);
    }

    private void drawBlock(GraphicsContext gc, GanttEntry entry) {
        double x = xOff + entry.startTime() * cellW;
        double w = (entry.endTime() - entry.startTime()) * cellW;

        Color color = entry.pid() == -1
                ? Color.web("#1e2d3d")
                : colorMap.getOrDefault(entry.pid(), Color.GRAY);

        // Fondo oscuro
        gc.setFill(color.deriveColor(0, 1, 0.2, 1));
        gc.fillRoundRect(x + 1, yBar, w - 2, barH, 6, 6);

        // Fill semitransparente
        gc.setFill(color.deriveColor(0, 1, 1, 0.30));
        gc.fillRoundRect(x + 1, yBar, w - 2, barH, 6, 6);

        // Borde
        gc.setStroke(color.deriveColor(0, 1, 1, 0.80));
        gc.setLineWidth(1.5);
        gc.setLineDashes(null);
        gc.strokeRoundRect(x + 1, yBar, w - 2, barH, 6, 6);

        // Texto
        String label = entry.pid() == -1 ? "IDLE" : "P" + entry.pid();
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        double tw = label.length() * 6.5;
        if (w > tw + 4) {
            gc.fillText(label, x + w / 2 - tw / 2, yBar + barH / 2 + 4);
        }
    }

    /** Redibujar sin el borde amarillo (llamado cuando avanza el indicador) */
    private void redrawBlock(GraphicsContext gc, GanttEntry entry) {
        // Borrar área del indicador (triángulo + borde extra)
        double x = xOff + entry.startTime() * cellW;
        double w = (entry.endTime() - entry.startTime()) * cellW;
        gc.setFill(Color.web("#0a0c10"));
        gc.fillRect(x - 2, yBar - 14, w + 4, 14); // zona del triángulo
        drawBlock(gc, entry);
    }

    /**
     * Divisor de quantum para Round Robin.
     *
     * Para el bloque en posición [idx]:
     *  - Si el bloque anterior es del mismo proceso → línea PUNTEADA en el límite entre ambos
     *    (significa que el mismo proceso continúa en otro quantum)
     *  - Si el bloque anterior es de un proceso distinto (o idle) → ya el borde del bloque
     *    es suficiente para indicar cambio de proceso, no dibujamos nada extra
     */
    private void drawQuantumDivider(GraphicsContext gc, int idx) {
        if (!isRR || idx <= 0 || idx >= ganttEntries.size()) return;

        GanttEntry cur  = ganttEntries.get(idx);
        GanttEntry prev = ganttEntries.get(idx - 1);

        if (cur.pid() == -1 || prev.pid() == -1) return;

        double divX = xOff + cur.startTime() * cellW;

        if (cur.pid() == prev.pid()) {
            // MISMO proceso: línea punteada blanca brillante = "este proceso recibió otro quantum"
            gc.setStroke(Color.web("#ffffffee"));
            gc.setLineWidth(1.8);
            gc.setLineDashes(5, 3);
            gc.strokeLine(divX, yBar + 3, divX, yBar + barH - 3);
            gc.setLineDashes(null);

            // Etiqueta "Q" encima con fondo
            gc.setFill(Color.web("#f59e0b"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
            gc.fillText("Q" + (idx), divX - 5, yBar - 2);
        } else {
            // DISTINTO proceso: línea sólida gris en el borde
            gc.setStroke(Color.web("#475569"));
            gc.setLineWidth(1.5);
            gc.setLineDashes(null);
            gc.strokeLine(divX, yBar, divX, yBar + barH);
        }
    }

    /** Indicador amarillo del bloque que se está dibujando actualmente */
    private void highlightCurrent(GraphicsContext gc, GanttEntry entry) {
        double x = xOff + entry.startTime() * cellW;
        double w = (entry.endTime() - entry.startTime()) * cellW;

        // Borde amarillo
        gc.setStroke(Color.web("#f59e0b"));
        gc.setLineWidth(2.0);
        gc.setLineDashes(null);
        gc.strokeRoundRect(x + 1, yBar, w - 2, barH, 6, 6);

        // Triángulo indicador encima
        double cx = x + w / 2;
        gc.setFill(Color.web("#f59e0b"));
        gc.fillPolygon(
            new double[]{cx - 5, cx + 5, cx},
            new double[]{yBar - 10, yBar - 10, yBar - 3},
            3
        );
    }

    private void drawTickLabel(GraphicsContext gc, int tick) {
        double x = xOff + tick * cellW;
        gc.setFill(Color.web("#64748b"));
        gc.setFont(Font.font("Segoe UI", 9));
        gc.fillText(String.valueOf(tick), x - 3, yBar + barH + 14);
        gc.setStroke(Color.web("#2d3748"));
        gc.setLineWidth(1);
        gc.strokeLine(x, yBar + barH + 1, x, yBar + barH + 5);
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }
}
