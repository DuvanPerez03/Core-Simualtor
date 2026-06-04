package com.coresimulator.view;

import com.coresimulator.model.MemoryFrame;
import com.coresimulator.model.Process;
import com.coresimulator.service.MemoryManager;
import com.coresimulator.service.SimulatorFacade;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * Vista de gestión de memoria.
 * - Barra de controles en dos filas (no se amontona)
 * - Slider de velocidad corregido (derecha = más rápido)
 * - Animación sin bug de reset: replay incremental correcto
 * - Info de capacidad: páginas necesarias vs marcos disponibles
 * Capa: View (Presentación)
 */
public class MemoryView extends BorderPane {

    private final SimulatorFacade facade;

    // Controls
    private ComboBox<String> algoCombo;
    private Spinner<Integer> framesSpinner;
    private Slider speedSlider;
    private Button btnRun, btnStop;

    // Stats
    private Label pageFaultsLabel, completedProcessesLabel, replacementsLabel;

    // Frames grid
    private GridPane framesGrid;

    // Events table
    private TableView<MemoryManager.PageEvent> eventTable;

    // Animation
    private Timeline animation;
    private List<MemoryManager.PageEvent> allEvents;   // todos los eventos de la simulación completa
    private MemoryFrame[] snapshotFrames;                // snapshot del estado final para replay
    private int animStep;
    private int currentFrameCount;

    // Colores fijos por proceso (hasta 10 procesos)
    private static final String[] PROC_COLORS = {
        "#00d4ff", "#7c3aed", "#10b981", "#f59e0b", "#ef4444",
        "#ec4899", "#06b6d4", "#84cc16", "#f97316", "#a855f7"
    };

    /**
     * Construye la vista de gestión de memoria.
     *
     * @param facade fachada de servicios del simulador
     */
    public MemoryView(SimulatorFacade facade) {
        this.facade = facade;
        this.currentFrameCount = facade.getMemoryManager().getTotalFrames();
        build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BUILD
    // ─────────────────────────────────────────────────────────────────────────

    private void build() {
        setStyle("-fx-background-color: transparent;");

        VBox header = new VBox(4);
        Label title = new Label("🗄  Gestión de Memoria");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Paginación por demanda · FIFO y LRU");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16, 0, 0, 0));
        content.getChildren().addAll(
            buildControls(),
            buildCapacityBar(),
            buildStatsRow(),
            buildFramesCard(),
            buildEventsCard()
        );

        VBox main = new VBox(0, header, content);

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONTROLES — dos filas para que no se apriete
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildControls() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));

        // Fila 1: algoritmo + marcos
        HBox row1 = new HBox(16);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label algoLabel = new Label("Algoritmo de reemplazo:");
        algoLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        algoCombo = new ComboBox<>();
        algoCombo.getItems().addAll("FIFO (First In First Out)", "LRU (Least Recently Used)");
        algoCombo.setValue("FIFO (First In First Out)");
        algoCombo.setPrefWidth(240);
        algoCombo.setStyle("-fx-background-color: #0d0f14; -fx-text-fill: #e2e8f0; " +
                "-fx-border-color: #2d3748; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label framesLabel = new Label("Número de marcos:");
        framesLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        framesSpinner = new Spinner<>(4, 32, currentFrameCount, 2);
        framesSpinner.setEditable(true);
        framesSpinner.setPrefWidth(90);
        framesSpinner.valueProperty().addListener((obs, o, n) -> updateCapacityLabel());

        row1.getChildren().addAll(algoLabel, algoCombo, framesLabel, framesSpinner);

        // Fila 2: velocidad + botones
        HBox row2 = new HBox(16);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label speedLabel = new Label("Velocidad de animación:");
        speedLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        // Slider: 100ms (rápido, derecha) a 1500ms (lento, izquierda)
        // Para que "derecha = rápido", usamos valor invertido internamente
        speedSlider = new Slider(100, 1500, 700);
        speedSlider.setPrefWidth(160);
        speedSlider.setShowTickLabels(false);

        Label lento = new Label("Lento");
        lento.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
        Label rapido = new Label("Rápido");
        rapido.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");

        // lento ←slider→ rápido
        HBox sliderBox = new HBox(6, lento, speedSlider, rapido);
        sliderBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnStop = new Button("⏹  Detener");
        btnStop.getStyleClass().add("btn-secondary");
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> stopAnimation());

        btnRun = new Button("▶  Simular Memoria");
        btnRun.getStyleClass().add("btn-primary");
        btnRun.setOnAction(e -> startSimulation());

        row2.getChildren().addAll(speedLabel, sliderBox, spacer, btnStop, btnRun);

        card.getChildren().addAll(row1, new Separator(), row2);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BARRA DE CAPACIDAD: páginas requeridas vs marcos disponibles
    // ─────────────────────────────────────────────────────────────────────────

    private FlowPane capacityPane;

    private FlowPane buildCapacityBar() {
        capacityPane = new FlowPane();
        capacityPane.setHgap(8);
        capacityPane.setVgap(6);
        capacityPane.getStyleClass().add("card");
        capacityPane.setPadding(new Insets(10, 16, 10, 16));
        updateCapacityLabel();
        return capacityPane;
    }

    private void updateCapacityLabel() {
        if (capacityPane == null) return;
        capacityPane.getChildren().clear();

        List<Process> procs = facade.getProcessList();
        int totalPages = procs.stream().mapToInt(Process::getMemoryPages).sum();
        int frames = framesSpinner != null ? framesSpinner.getValue() : currentFrameCount;
        boolean warn = totalPages > frames;

        // Chip: marcos disponibles
        capacityPane.getChildren().add(chip(
            "🔲 Marcos disponibles: " + frames, "#00d4ff", "#00d4ff22"));

        // Chip: total páginas
        capacityPane.getChildren().add(chip(
            "📄 Páginas totales: " + totalPages,
            warn ? "#f59e0b" : "#10b981",
            warn ? "#f59e0b22" : "#10b98122"));

        // Chip por proceso
        for (Process p : procs) {
            capacityPane.getChildren().add(chip(
                p.getName() + ": " + p.getMemoryPages() + " págs.",
                "#94a3b8", "#1e293b"));
        }

        // Chip de estado
        if (warn) {
            capacityPane.getChildren().add(chip(
                "⚠ Faltan " + (totalPages - frames) + " marcos — habrá reemplazos",
                "#f59e0b", "#f59e0b22"));
        } else {
            capacityPane.getChildren().add(chip(
                "✓ Marcos suficientes para todos los procesos",
                "#10b981", "#10b98122"));
        }
    }

    private Label chip(String text, String textColor, String bgColor) {
        Label lbl = new Label(text);
        lbl.setStyle(
            "-fx-text-fill: " + textColor + "; -fx-font-size: 11px; " +
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: 20; -fx-border-color: " + textColor + "44; " +
            "-fx-border-radius: 20; -fx-padding: 3 10 3 10;");
        return lbl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  STATS
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildStatsRow() {
        pageFaultsLabel = new Label("—");
        pageFaultsLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");

        completedProcessesLabel = new Label("—");
        completedProcessesLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #00d4ff;");

        replacementsLabel = new Label("—");
        replacementsLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        VBox faultsCard       = makeStatCard("Page Faults",          pageFaultsLabel);
        VBox totalCard        = makeStatCard("Procesos completados",  completedProcessesLabel);
        VBox replacementsCard = makeStatCard("Reemplazos realizados", replacementsLabel);

        HBox row = new HBox(12, faultsCard, totalCard, replacementsCard);
        HBox.setHgrow(faultsCard,       Priority.ALWAYS);
        HBox.setHgrow(totalCard,        Priority.ALWAYS);
        HBox.setHgrow(replacementsCard, Priority.ALWAYS);
        return row;
    }

    private VBox makeStatCard(String label, Label valueLabel) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        card.getChildren().addAll(valueLabel, lbl);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FRAMES GRID
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildFramesCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("🔲  Estado de Marcos de Página");
        title.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        framesGrid = new GridPane();
        framesGrid.setHgap(8);
        framesGrid.setVgap(8);
        framesGrid.setPadding(new Insets(8, 0, 0, 0));

        renderFrames(facade.getMemoryManager().getFrames(), -1, false);

        HBox legend = new HBox(16);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
            legendItem("#0c2a3d", "#00d4ff66", "Ocupado"),
            legendItem("#141820", "#2d3748",   "Libre"),
            legendItem("#1a1000", "#f59e0b",   "Recién modificado")
        );

        card.getChildren().addAll(title, framesGrid, legend);
        return card;
    }

    private HBox legendItem(String bg, String border, String label) {
        HBox item = new HBox(6);
        item.setAlignment(Pos.CENTER_LEFT);
        Rectangle rect = new Rectangle(14, 14);
        rect.setFill(Color.web(bg));
        rect.setStroke(Color.web(border));
        rect.setStrokeWidth(1.5);
        rect.setArcWidth(4);
        rect.setArcHeight(4);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        item.getChildren().addAll(rect, lbl);
        return item;
    }

    /**
     * Renderiza los marcos. highlightFrame = id del marco recién tocado (-1 = ninguno).
     * Si highlight=true pinta el borde amarillo.
     */
    private void renderFrames(MemoryFrame[] frames, int highlightFrameId, boolean highlight) {
        framesGrid.getChildren().clear();
        int cols = 8;
        for (int i = 0; i < frames.length; i++) {
            VBox cell = buildFrameCell(frames[i], highlight && frames[i].getFrameId() == highlightFrameId);
            framesGrid.add(cell, i % cols, i / cols);
        }
    }

    private String procColor(int pid) {
        return PROC_COLORS[Math.abs(pid - 1) % PROC_COLORS.length];
    }

    private VBox buildFrameCell(MemoryFrame f, boolean highlight) {
        VBox cell = new VBox(2);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(82, 64);
        cell.setMinSize(82, 64);
        cell.setPadding(new Insets(6));

        if (f.isFree()) {
            cell.setStyle("-fx-background-color: #141820; -fx-border-color: #2d3748; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");
            Label fn = new Label("Marco " + f.getFrameId());
            fn.setStyle("-fx-text-fill: #2d3748; -fx-font-size: 10px;");
            Label em = new Label("Libre");
            em.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
            cell.getChildren().addAll(fn, em);
        } else {
            String color  = highlight ? "#f59e0b"  : procColor(f.getProcessPid());
            String bg     = highlight ? "#1a1000"  : color + "18";
            String border = highlight ? "#f59e0b"  : color + "66";
            int bw        = highlight ? 2 : 1;
            cell.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: " + bw + ";");
            Label fn = new Label("Marco " + f.getFrameId());
            fn.setStyle("-fx-text-fill: " + color + "88; -fx-font-size: 9px;");
            Label pg = new Label("Pág. " + f.getPageNumber());
            pg.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label pr = new Label(f.getProcessName());
            pr.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 9px;");
            pr.setMaxWidth(78);
            cell.getChildren().addAll(fn, pg, pr);
        }
        return cell;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EVENTS TABLE
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildEventsCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        Label title = new Label("📋  Registro de Eventos de Memoria");
        title.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        eventTable = new TableView<>();
        eventTable.getStyleClass().add("table-view");
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        eventTable.setPrefHeight(200);
        eventTable.setPlaceholder(new Label("Ejecuta la simulación para ver los eventos."));
        VBox.setVgrow(eventTable, Priority.ALWAYS);

        TableColumn<MemoryManager.PageEvent, Integer> c1 = col("Tick",    50, cd -> new javafx.beans.property.SimpleIntegerProperty(cd.getValue().tick()).asObject());
        TableColumn<MemoryManager.PageEvent, Integer> c2 = col("PID",     50, cd -> new javafx.beans.property.SimpleIntegerProperty(cd.getValue().pid()).asObject());
        TableColumn<MemoryManager.PageEvent, String>  c3 = colS("Proceso",  0, cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().processName()));
        TableColumn<MemoryManager.PageEvent, Integer> c4 = col("Página",  60, cd -> new javafx.beans.property.SimpleIntegerProperty(cd.getValue().pageNumber()).asObject());
        TableColumn<MemoryManager.PageEvent, Integer> c5 = col("Marco",   60, cd -> new javafx.beans.property.SimpleIntegerProperty(cd.getValue().frameId()).asObject());
        TableColumn<MemoryManager.PageEvent, String>  c6 = colS("Evento", 110, cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().pageFault() ? "Page Fault" : "✓ Hit"));
        TableColumn<MemoryManager.PageEvent, String>  c7 = colS("Detalles", 0, cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().details()));

        eventTable.getColumns().addAll(c1, c2, c3, c4, c5, c6, c7);
        card.getChildren().addAll(title, eventTable);
        return card;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<MemoryManager.PageEvent, T> col(String name, double maxW,
            javafx.util.Callback<TableColumn.CellDataFeatures<MemoryManager.PageEvent, T>,
                    javafx.beans.value.ObservableValue<T>> factory) {
        TableColumn<MemoryManager.PageEvent, T> c = new TableColumn<>(name);
        c.setCellValueFactory(factory);
        if (maxW > 0) c.setMaxWidth(maxW);
        return c;
    }

    private TableColumn<MemoryManager.PageEvent, String> colS(String name, double maxW,
            javafx.util.Callback<TableColumn.CellDataFeatures<MemoryManager.PageEvent, String>,
                    javafx.beans.value.ObservableValue<String>> factory) {
        return col(name, maxW, factory);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LÓGICA DE SIMULACIÓN Y ANIMACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private void startSimulation() {
        if (facade.getProcessList().isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                "No hay procesos cargados. Usa 'Cargar Procesos de Ejemplo' primero.").show();
            return;
        }

        stopAnimation();

        // Actualizar marcos si cambió
        int requestedFrames = framesSpinner.getValue();
        if (requestedFrames != currentFrameCount) {
            facade.rebuildMemoryManager(requestedFrames);
            currentFrameCount = requestedFrames;
        }

        // 1. Correr simulación completa para obtener todos los eventos
        boolean isLRU = algoCombo.getValue().startsWith("LRU");
        if (isLRU) facade.simulateMemoryLRU();
        else       facade.simulateMemoryFIFO();

        // 2. Guardar los eventos ANTES de resetear
        allEvents = facade.getMemoryManager().getEventLog();
        animStep  = 0;

        // 3. Resetear el servicio para que la animación lo reconstruya paso a paso
        //    desde cero — sin mezclar con la simulación ya hecha
        facade.getMemoryManager().reset();

        // 4. Limpiar UI
        eventTable.getItems().clear();
        resetStats();
        renderFrames(facade.getMemoryManager().getFrames(), -1, false);

        btnRun.setDisable(true);
        btnStop.setDisable(false);

        // Velocidad: slider va de 100 (rápido) a 1500 (lento)
        // Pero lo mostramos como lento←→rápido, así que
        // delay real = 1600 - valor del slider
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (animation != null && animStep < allEvents.size()) {
                animation.stop();
                double nd = 1600.0 - newVal.doubleValue();
                animation = new Timeline(new KeyFrame(Duration.millis(nd), e -> animateStep()));
                animation.setCycleCount(allEvents.size() - animStep);
                animation.setOnFinished(e -> onAnimationFinished());
                animation.play();
            }
        });

        double delayMs = 1600.0 - speedSlider.getValue();
        animation = new Timeline(new KeyFrame(Duration.millis(delayMs), e -> animateStep()));
        animation.setCycleCount(allEvents.size());
        animation.setOnFinished(e -> onAnimationFinished());
        animation.play();
    }

    /**
     * Avanza un paso en la animación.
     * Usa los eventos pre-calculados para actualizar el estado de los frames
     * directamente — sin volver a llamar accessPage (que generaría eventos nuevos
     * y corrompería el frameId al mezclarlos con los guardados).
     */
    private void animateStep() {
        if (animStep >= allEvents.size()) return;

        MemoryManager.PageEvent ev = allEvents.get(animStep);
        animStep++;

        // Aplicar este evento directamente sobre el array de frames
        // sin pasar por accessPage (evita contaminar el eventLog interno)
        applyEventToFrames(ev);

        // Actualizar tabla
        eventTable.getItems().add(ev);
        eventTable.scrollTo(eventTable.getItems().size() - 1);

        // Stats parciales
        long faults       = allEvents.subList(0, animStep).stream().filter(MemoryManager.PageEvent::pageFault).count();
        long replacements = allEvents.subList(0, animStep).stream()
                .filter(e2 -> e2.pageFault() && e2.details().contains("Reemplazo")).count();
        pageFaultsLabel.setText(String.valueOf(faults));
        // Un proceso está completo si todos sus eventos ya fueron procesados
        long done = facade.getProcessList().stream()
                .filter(pr -> allEvents.subList(0, animStep).stream()
                        .filter(e2 -> e2.pid() == pr.getPid()).count() >= pr.getPagesNeeded())
                .count();
        completedProcessesLabel.setText(done + " / " + facade.getProcessList().size());
        replacementsLabel.setText(String.valueOf(replacements));

        // Renderizar con el marco del evento resaltado
        renderFrames(facade.getMemoryManager().getFrames(), ev.frameId(), true);
    }

    /**
     * Aplica un evento pre-calculado directamente sobre el frame indicado.
     * Solo actúa si fue un page fault (carga o reemplazo) — los hits no cambian los frames.
     */
    private void applyEventToFrames(MemoryManager.PageEvent ev) {
        if (!ev.pageFault()) {
            // Hit: solo actualizar lastUsedTime del frame (para LRU visual)
            MemoryFrame f = facade.getMemoryManager().getFrames()[ev.frameId()];
            f.setLastUsedTime(ev.tick());
            return;
        }
        // Page fault: cargar la página en el frame indicado por el evento
        MemoryFrame target = facade.getMemoryManager().getFrames()[ev.frameId()];
        target.load(ev.pageNumber(), ev.pid(), ev.processName(), ev.tick());
    }

    private void onAnimationFinished() {
        // Render final sin resaltado
        renderFrames(facade.getMemoryManager().getFrames(), -1, false);
        btnRun.setDisable(false);
        btnStop.setDisable(true);
        animation = null;
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
        btnRun.setDisable(false);
        btnStop.setDisable(true);
    }

    private void resetStats() {
        pageFaultsLabel.setText("0");
        completedProcessesLabel.setText("0 / " + facade.getProcessList().size());
        replacementsLabel.setText("0");
    }

    /** Copia profunda del array de frames para snapshot */
    private MemoryFrame[] deepCopyFrames(MemoryFrame[] src) {
        MemoryFrame[] copy = new MemoryFrame[src.length];
        for (int i = 0; i < src.length; i++) {
            MemoryFrame s = src[i];
            MemoryFrame f = new MemoryFrame(s.getFrameId());
            if (!s.isFree()) {
                f.load(s.getPageNumber(), s.getProcessPid(), s.getProcessName(), s.getLoadTime());
                f.setLastUsedTime(s.getLastUsedTime());
            }
            copy[i] = f;
        }
        return copy;
    }
}
