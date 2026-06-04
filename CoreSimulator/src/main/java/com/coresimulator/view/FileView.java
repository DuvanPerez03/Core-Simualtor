package com.coresimulator.view;

import com.coresimulator.model.Process;
import com.coresimulator.model.SharedFile.AccessType;
import com.coresimulator.service.FileAccessManager;
import com.coresimulator.service.SimulatorFacade;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;

/**
 * Vista de gestion de archivos compartidos.
 * Permite configurar que proceso accede a que archivo y con que tipo de operacion,
 * luego anima el resultado paso a paso mostrando bloqueos y esperas en tiempo real.
 */
public class FileView extends BorderPane {

    private final SimulatorFacade facade;

    // Solicitudes de acceso configuradas por el usuario
    private final List<AccessRequest> pendingRequests = new ArrayList<>();
    private VBox requestsBox;

    // Archivos disponibles en el sistema simulado
    private static final String[] FILE_NAMES = {"datos.txt", "config.sys", "log.txt"};

    // Labels de estado por archivo — se actualizan durante la animacion
    private final Map<String, Label> fileStatusLabels = new LinkedHashMap<>();
    private final Map<String, Label> fileOwnerLabels  = new LinkedHashMap<>();

    // Tabla de eventos generados
    private TableView<FileAccessManager.AccessEvent> logTable;

    // Contadores de metricas
    private Label conflictsLabel, totalLabel, mutexLabel;

    // Animacion paso a paso
    private Slider speedSlider;
    private Timeline animation;
    private List<FileAccessManager.AccessEvent> allEvents;
    private int animStep;

    // Rastreo del proceso que tiene el mutex por archivo (para mostrar "esperando a PX")
    private final Map<String, Integer> fileLockOwner = new HashMap<>();

    // Panel del informe de conflictos — aparece al terminar la simulacion
    private VBox conflictReportBox;

    /**
     * Construye la vista para simular accesos a archivos compartidos.
     *
     * @param facade fachada de servicios del simulador
     */
    public FileView(SimulatorFacade facade) {
        this.facade = facade;
        build();
    }

    private void build() {
        setStyle("-fx-background-color: transparent;");

        VBox header = new VBox(4);
        Label title = new Label("Gestion de Archivos Compartidos");
        title.getStyleClass().add("page-title");
        Label sub = new Label(
            "Configura que proceso accede a que archivo y simula los conflictos de mutex paso a paso.");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        conflictReportBox = new VBox(8);
        conflictReportBox.setVisible(false);
        conflictReportBox.setManaged(false);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16, 0, 0, 0));
        content.getChildren().addAll(
            buildStatsRow(),
            buildMainArea(),
            buildLogCard(),
            conflictReportBox
        );

        VBox main = new VBox(0, header, content);
        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private HBox buildStatsRow() {
        conflictsLabel = new Label("—");
        conflictsLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");

        totalLabel = new Label("—");
        totalLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #00d4ff;");

        mutexLabel = new Label("—");
        mutexLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        VBox c1 = makeStatCard("Conflictos detectados", conflictsLabel);
        VBox c2 = makeStatCard("Accesos totales",        totalLabel);
        VBox c3 = makeStatCard("Bloqueos por mutex",     mutexLabel);

        HBox row = new HBox(12, c1, c2, c3);
        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        return row;
    }

    private VBox makeStatCard(String label, Label valueLabel) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        card.getChildren().addAll(valueLabel, lbl);
        return card;
    }

    /**
     * Area principal dividida en dos columnas:
     * izquierda para configurar accesos, derecha para ver el estado de los archivos.
     */
    private HBox buildMainArea() {
        HBox area = new HBox(14);

        VBox leftPanel = buildRequestPanel();
        leftPanel.setMinWidth(420);
        leftPanel.setMaxWidth(420);

        VBox rightPanel = buildFilesPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        area.getChildren().addAll(leftPanel, rightPanel);
        return area;
    }

    /**
     * Panel izquierdo: selector de proceso, archivo y tipo de acceso,
     * lista de solicitudes configuradas, slider de velocidad y botones.
     */
    private VBox buildRequestPanel() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label t = new Label("Configurar accesos");
        t.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        HBox selector = new HBox(8);
        selector.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> procCombo = new ComboBox<>();
        procCombo.setPromptText("Proceso");
        procCombo.setPrefWidth(130);
        refreshProcessCombo(procCombo);

        ComboBox<String> fileCombo = new ComboBox<>();
        fileCombo.getItems().addAll(FILE_NAMES);
        fileCombo.setValue(FILE_NAMES[0]);
        fileCombo.setPrefWidth(110);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("ESCRITURA", "LECTURA");
        typeCombo.setValue("ESCRITURA");
        typeCombo.setPrefWidth(100);

        Button btnAdd = new Button("Agregar");
        btnAdd.getStyleClass().add("btn-secondary");
        btnAdd.setOnAction(e -> {
            String procVal = procCombo.getValue();
            if (procVal == null) return;
            int pid = Integer.parseInt(procVal.split(" ")[0].replace("P", ""));
            String procName = procVal.contains(" - ") ? procVal.split(" - ")[1] : procVal;
            AccessType type = typeCombo.getValue().equals("ESCRITURA")
                    ? AccessType.WRITE : AccessType.READ;
            pendingRequests.add(new AccessRequest(pid, procName, fileCombo.getValue(), type));
            refreshRequestsBox();
        });

        selector.getChildren().addAll(procCombo, fileCombo, typeCombo, btnAdd);

        requestsBox = new VBox(6);
        Label requestsTitle = new Label("Solicitudes configuradas:");
        requestsTitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        refreshRequestsBox();

        // Slider de velocidad: izquierda=lento, derecha=rapido
        HBox speedRow = new HBox(10);
        speedRow.setAlignment(Pos.CENTER_LEFT);
        Label speedLbl = new Label("Velocidad:");
        speedLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label lentoLbl = new Label("Lento");
        lentoLbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
        Label rapidoLbl = new Label("Rapido");
        rapidoLbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
        // Rango 100ms-5000ms; delay = 5100 - valor => izquierda(100)=5000ms, derecha(5000)=100ms
        speedSlider = new Slider(100, 5000, 1500);
        speedSlider.setPrefWidth(140);
        HBox sliderBox = new HBox(6, lentoLbl, speedSlider, rapidoLbl);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        speedRow.getChildren().addAll(speedLbl, sliderBox);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnPreset = new Button("Cargar ejemplo");
        btnPreset.getStyleClass().add("btn-secondary");
        btnPreset.setPrefWidth(140);
        btnPreset.setOnAction(e -> loadPresetScenario());

        Button btnClear = new Button("Limpiar");
        btnClear.getStyleClass().add("btn-secondary");
        btnClear.setPrefWidth(90);
        btnClear.setOnAction(e -> { pendingRequests.clear(); refreshRequestsBox(); });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRun = new Button("Simular");
        btnRun.getStyleClass().add("btn-primary");
        btnRun.setPrefWidth(100);
        btnRun.setOnAction(e -> startSimulation());

        actions.getChildren().addAll(btnPreset, btnClear, sp, btnRun);

        card.getChildren().addAll(t, new Separator(), selector,
                requestsTitle, requestsBox, new Separator(), speedRow, actions);
        return card;
    }

    private void refreshProcessCombo(ComboBox<String> combo) {
        combo.getItems().clear();
        for (Process p : facade.getProcessList()) {
            combo.getItems().add("P" + p.getPid() + " - " + p.getName());
        }
        if (!combo.getItems().isEmpty()) combo.setValue(combo.getItems().get(0));
    }

    private void refreshRequestsBox() {
        requestsBox.getChildren().clear();
        if (pendingRequests.isEmpty()) {
            Label empty = new Label("Ninguna solicitud configurada aun.");
            empty.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
            requestsBox.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < pendingRequests.size(); i++) {
            AccessRequest req = pendingRequests.get(i);
            final int idx = i;

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle("-fx-background-color: #1a2030; -fx-background-radius: 6;");

            String typeColor = req.type == AccessType.WRITE ? "#ef4444" : "#10b981";
            String typeText  = req.type == AccessType.WRITE ? "ESCRITURA" : "LECTURA";

            Label procLbl = new Label("P" + req.pid + " " + req.processName);
            procLbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12px;");

            Label arrow = new Label("-->");
            arrow.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");

            Label fileLbl = new Label(req.fileName);
            fileLbl.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 12px;");

            Label typeLbl = new Label(typeText);
            typeLbl.setStyle("-fx-text-fill: " + typeColor + "; -fx-font-size: 11px; " +
                    "-fx-background-color: " + typeColor + "22; -fx-background-radius: 4; -fx-padding: 1 6;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button del = new Button("x");
            del.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a5568; " +
                    "-fx-cursor: hand; -fx-font-size: 11px;");
            del.setOnAction(e -> { pendingRequests.remove(idx); refreshRequestsBox(); });

            row.getChildren().addAll(procLbl, arrow, fileLbl, typeLbl, spacer, del);
            requestsBox.getChildren().add(row);
        }
    }

    /**
     * Panel derecho: muestra el estado actual de cada archivo
     * (LIBRE, LEYENDO, BLOQUEADO o ESPERANDO) y quien lo esta usando.
     */
    private VBox buildFilesPanel() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label t = new Label("Estado de archivos en tiempo real");
        t.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        VBox filesBox = new VBox(10);
        for (String fileName : FILE_NAMES) {
            VBox fileItem = new VBox(6);
            fileItem.setPadding(new Insets(12));
            fileItem.setStyle("-fx-background-color: #1a2030; -fx-background-radius: 8;");

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLbl = new Label(fileName);
            nameLbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label statusLbl = new Label("LIBRE");
            statusLbl.setStyle("-fx-background-color: #064e3b; -fx-text-fill: #10b981; " +
                    "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            fileStatusLabels.put(fileName, statusLbl);

            row.getChildren().addAll(nameLbl, sp, statusLbl);

            Label ownerLbl = new Label("Sin accesos activos");
            ownerLbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
            fileOwnerLabels.put(fileName, ownerLbl);

            fileItem.getChildren().addAll(row, ownerLbl);
            filesBox.getChildren().add(fileItem);
        }

        VBox legend = new VBox(4);
        legend.setStyle("-fx-border-color: #1e2d3d; -fx-border-radius: 6; " +
                "-fx-border-width: 1; -fx-padding: 8;");
        Label legendTitle = new Label("Referencia:");
        legendTitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");
        for (Label l : new Label[]{
            new Label("LIBRE       : ningun proceso esta accediendo"),
            new Label("LEYENDO     : uno o mas procesos leen simultaneamente (sin conflicto)"),
            new Label("BLOQUEADO   : un proceso escribe — los demas deben esperar"),
            new Label("ESPERANDO   : proceso bloqueado intentando entrar")
        }) {
            l.setStyle("-fx-text-fill: #4a5568; -fx-font-size: 10px;");
            legend.getChildren().add(l);
        }
        legend.getChildren().add(0, legendTitle);

        card.getChildren().addAll(t, new Separator(), filesBox, legend);
        return card;
    }

    private VBox buildLogCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label t = new Label("Registro de eventos");
        t.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        logTable = new TableView<>();
        logTable.getStyleClass().add("table-view");
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        logTable.setPrefHeight(200);
        logTable.setPlaceholder(new Label("Configura los accesos y presiona Simular."));

        TableColumn<FileAccessManager.AccessEvent, Integer> c1 = new TableColumn<>("Tick");
        c1.setCellValueFactory(cd -> new javafx.beans.property.SimpleIntegerProperty(
                cd.getValue().tick()).asObject());
        c1.setMaxWidth(50);

        TableColumn<FileAccessManager.AccessEvent, String> c2 = new TableColumn<>("Proceso");
        c2.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                "P" + cd.getValue().pid() + " " + cd.getValue().processName()));

        TableColumn<FileAccessManager.AccessEvent, String> c3 = new TableColumn<>("Archivo");
        c3.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().fileName()));

        TableColumn<FileAccessManager.AccessEvent, String> c4 = new TableColumn<>("Tipo");
        c4.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().type() == AccessType.WRITE ? "ESCRITURA" : "LECTURA"));
        c4.setMaxWidth(80);

        TableColumn<FileAccessManager.AccessEvent, String> c5 = new TableColumn<>("Resultado");
        c5.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().conflict() ? "BLOQUEADO" : cd.getValue().status()));

        logTable.getColumns().addAll(c1, c2, c3, c4, c5);
        card.getChildren().addAll(t, logTable);
        return card;
    }

    /**
     * Escenario predefinido con conflictos garantizados:
     * P1 y P2 compiten por escritura en datos.txt,
     * P3 intenta leer mientras hay escritor activo,
     * P2 y P1 compiten por log.txt en orden inverso.
     */
    private void loadPresetScenario() {
        pendingRequests.clear();
        List<Process> procs = facade.getProcessList();
        if (procs.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                "No hay procesos. Carga los procesos de ejemplo primero.").show();
            return;
        }

        Process p1 = procs.get(0);
        Process p2 = procs.size() > 1 ? procs.get(1) : p1;
        Process p3 = procs.size() > 2 ? procs.get(2) : p1;
        Process p4 = procs.size() > 3 ? procs.get(3) : p2;

        // P1 escribe datos.txt — adquiere mutex
        pendingRequests.add(new AccessRequest(p1.getPid(), p1.getName(), "datos.txt", AccessType.WRITE));
        // P2 intenta escribir datos.txt — BLOQUEADO (P1 tiene el mutex)
        pendingRequests.add(new AccessRequest(p2.getPid(), p2.getName(), "datos.txt", AccessType.WRITE));
        // P3 intenta leer datos.txt — BLOQUEADO (hay escritor activo)
        pendingRequests.add(new AccessRequest(p3.getPid(), p3.getName(), "datos.txt", AccessType.READ));
        // P4 lee datos.txt — entra cuando ya estan libres
        pendingRequests.add(new AccessRequest(p4.getPid(), p4.getName(), "datos.txt", AccessType.READ));
        // P2 escribe log.txt — sin conflicto
        pendingRequests.add(new AccessRequest(p2.getPid(), p2.getName(), "log.txt", AccessType.WRITE));
        // P1 intenta escribir log.txt — BLOQUEADO (P2 tiene el mutex)
        pendingRequests.add(new AccessRequest(p1.getPid(), p1.getName(), "log.txt", AccessType.WRITE));

        refreshRequestsBox();
    }

    /**
     * Inicia la simulacion: genera todos los eventos, resetea la UI
     * y lanza la animacion paso a paso.
     */
    private void startSimulation() {
        if (pendingRequests.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                "Agrega al menos una solicitud antes de simular.").show();
            return;
        }

        if (animation != null) { animation.stop(); animation = null; }

        resetFileStatus();
        logTable.getItems().clear();
        fileLockOwner.clear();
        conflictReportBox.getChildren().clear();
        conflictReportBox.setVisible(false);
        conflictReportBox.setManaged(false);

        // Resetear contadores — se actualizaran incrementalmente
        conflictsLabel.setText("0");
        totalLabel.setText("0");
        mutexLabel.setText("0");

        allEvents = simulateRequests(pendingRequests);
        animStep  = 0;

        // delay = 5100 - valor del slider; izquierda=lento, derecha=rapido
        // El listener recrea el Timeline con el nuevo delay cuando el usuario mueve el slider
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (animation != null && animStep < allEvents.size()) {
                animation.stop();
                double newDelay = 5100.0 - newVal.doubleValue();
                animation = new Timeline(new KeyFrame(Duration.millis(newDelay), e -> animateStep()));
                animation.setCycleCount(allEvents.size() - animStep);
                animation.setOnFinished(ev2 -> buildConflictReport());
                animation.play();
            }
        });

        double delayMs = 5100.0 - speedSlider.getValue();
        animation = new Timeline(new KeyFrame(Duration.millis(delayMs), e -> animateStep()));
        animation.setCycleCount(allEvents.size());
        animation.setOnFinished(ev -> buildConflictReport());
        animation.play();
    }

    /**
     * Avanza un evento por paso.
     * El Timeline se recrea cada vez que cambia el slider para que la velocidad
     * se aplique de inmediato sin tener que detener y reiniciar la simulacion.
     */
    private void animateStep() {
        if (animStep >= allEvents.size()) return;
        FileAccessManager.AccessEvent ev = allEvents.get(animStep);
        animStep++;

        logTable.getItems().add(ev);
        logTable.scrollTo(logTable.getItems().size() - 1);
        updateFileVisual(ev);

        long conflicts = logTable.getItems().stream()
                .filter(FileAccessManager.AccessEvent::conflict).count();
        long blocked = logTable.getItems().stream()
                .filter(e -> e.status().toUpperCase().contains("BLOQUEADO")).count();
        conflictsLabel.setText(String.valueOf(conflicts));
        totalLabel.setText(String.valueOf(logTable.getItems().size()));
        mutexLabel.setText(String.valueOf(blocked));

        if (animStep >= allEvents.size()) {
            buildConflictReport();
        }
    }

    /**
     * Actualiza el color y texto del archivo segun el tipo de evento.
     * Prioriza BLOQUEADO/ESPERANDO sobre lectura/escritura para no confundir estados.
     */
    private void updateFileVisual(FileAccessManager.AccessEvent ev) {
        Label statusLbl = fileStatusLabels.get(ev.fileName());
        Label ownerLbl  = fileOwnerLabels.get(ev.fileName());
        if (statusLbl == null) return;

        String procLabel = "P" + ev.pid() + " " + ev.processName();
        String status = ev.status();

        if (status.contains("BLOQUEADO") || status.contains("esperando")) {
            // Proceso esperando — naranja
            statusLbl.setText("ESPERANDO");
            statusLbl.setStyle("-fx-background-color: #451a00; -fx-text-fill: #f59e0b; " +
                    "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            Integer owner = fileLockOwner.get(ev.fileName());
            String ownerStr = owner != null ? " — esperando a P" + owner : "";
            ownerLbl.setText(procLabel + " bloqueado" + ownerStr);
            ownerLbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 11px;");

        } else if (status.contains("Mutex adquirido") ||
                   (status.contains("escribiendo") && ev.type() == AccessType.WRITE)) {
            // Escritor activo — rojo
            fileLockOwner.put(ev.fileName(), ev.pid());
            statusLbl.setText("BLOQUEADO");
            statusLbl.setStyle("-fx-background-color: #450a0a; -fx-text-fill: #ef4444; " +
                    "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            ownerLbl.setText(procLabel + " tiene el mutex — escribiendo");
            ownerLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");

        } else if (status.contains("Lectura permitida") ||
                   (status.contains("leyendo") && ev.type() == AccessType.READ)) {
            // Lector activo — azul
            statusLbl.setText("LEYENDO");
            statusLbl.setStyle("-fx-background-color: #0c2a3d; -fx-text-fill: #00d4ff; " +
                    "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            ownerLbl.setText(procLabel + " esta leyendo");
            ownerLbl.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 11px;");

        } else if (status.contains("finalizada") || status.contains("liberado") ||
                   status.contains("finalizado") || status.contains("finalizada")) {
            // Acceso terminado — verde
            fileLockOwner.remove(ev.fileName());
            statusLbl.setText("LIBRE");
            statusLbl.setStyle("-fx-background-color: #064e3b; -fx-text-fill: #10b981; " +
                    "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            ownerLbl.setText("Sin accesos activos");
            ownerLbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
        }
    }

    private void resetFileStatus() {
        for (String f : FILE_NAMES) {
            Label s = fileStatusLabels.get(f);
            Label o = fileOwnerLabels.get(f);
            if (s != null) {
                s.setText("LIBRE");
                s.setStyle("-fx-background-color: #064e3b; -fx-text-fill: #10b981; " +
                        "-fx-background-radius: 4; -fx-padding: 2 8; -fx-font-size: 11px;");
            }
            if (o != null) {
                o.setText("Sin accesos activos");
                o.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");
            }
        }
    }

    /**
     * Genera los eventos simulando el patron Lectores-Escritores.
     * Agrupa solicitudes por archivo: el primer escritor adquiere el mutex,
     * todos los demas (escritores y lectores al mismo archivo) quedan bloqueados
     * y entran uno por uno cuando el escritor termina.
     * Los lectores pueden coexistir cuando no hay escritor activo.
     */
    /**
     * Genera los eventos en orden secuencial por archivo.
     * Primero se procesan todos los accesos a datos.txt, luego config.sys, luego log.txt.
     * Dentro de cada archivo el primer escritor adquiere el mutex y los demas esperan.
     */
    private List<FileAccessManager.AccessEvent> simulateRequests(List<AccessRequest> requests) {
        List<FileAccessManager.AccessEvent> events = new ArrayList<>();

        Map<String, List<AccessRequest>> byFile = new LinkedHashMap<>();
        for (String f : FILE_NAMES) byFile.put(f, new ArrayList<>());
        for (AccessRequest req : requests) byFile.get(req.fileName).add(req);

        int tick = 0;

        for (Map.Entry<String, List<AccessRequest>> entry : byFile.entrySet()) {
            String fileName = entry.getKey();
            List<AccessRequest> group = entry.getValue();
            if (group.isEmpty()) continue;

            List<AccessRequest> writers = group.stream()
                    .filter(r -> r.type == AccessType.WRITE).toList();
            List<AccessRequest> readers = group.stream()
                    .filter(r -> r.type == AccessType.READ).toList();

            if (writers.isEmpty()) {
                for (AccessRequest r : readers) {
                    events.add(new FileAccessManager.AccessEvent(tick, r.pid, r.processName,
                            fileName, r.type, false,
                            "Lectura permitida — " + readers.size() + " lector(es) en " + fileName));
                }
                tick++;
                for (AccessRequest r : readers) {
                    events.add(new FileAccessManager.AccessEvent(tick, r.pid, r.processName,
                            fileName, r.type, false, "Lectura finalizada"));
                }
                tick++;

            } else {
                AccessRequest first = writers.get(0);
                events.add(new FileAccessManager.AccessEvent(tick++, first.pid, first.processName,
                        fileName, first.type, false,
                        "Mutex adquirido — " + first.processName + " escribiendo en " + fileName));

                List<AccessRequest> waiting = new ArrayList<>(writers.subList(1, writers.size()));
                waiting.addAll(readers);

                for (AccessRequest r : waiting) {
                    String motivo = r.type == AccessType.WRITE
                            ? first.processName + " tiene el mutex — " + r.processName + " no puede escribir aun"
                            : first.processName + " esta escribiendo — " + r.processName + " debe esperar";
                    events.add(new FileAccessManager.AccessEvent(tick, r.pid, r.processName,
                            fileName, r.type, true, "BLOQUEADO — " + motivo));
                }
                if (!waiting.isEmpty()) tick++;

                events.add(new FileAccessManager.AccessEvent(tick++, first.pid, first.processName,
                        fileName, first.type, false,
                        "Escritura finalizada — mutex liberado por " + first.processName));

                for (AccessRequest r : waiting) {
                    if (r.type == AccessType.WRITE) {
                        events.add(new FileAccessManager.AccessEvent(tick++, r.pid, r.processName,
                                fileName, r.type, false,
                                "Mutex adquirido tras espera — " + r.processName + " escribiendo"));
                        events.add(new FileAccessManager.AccessEvent(tick++, r.pid, r.processName,
                                fileName, r.type, false,
                                "Escritura finalizada — mutex liberado"));
                    } else {
                        events.add(new FileAccessManager.AccessEvent(tick++, r.pid, r.processName,
                                fileName, r.type, false,
                                "Lectura permitida tras esperar — " + r.processName + " leyendo " + fileName));
                        events.add(new FileAccessManager.AccessEvent(tick++, r.pid, r.processName,
                                fileName, r.type, false,
                                "Lectura finalizada"));
                    }
                }
            }
        }
        return events;
    }

    /**
     * Construye el informe de conflictos al terminar la animacion.
     * Explica cada bloqueo: que proceso fue afectado, por quien y por que.
     */
    private void buildConflictReport() {
        List<FileAccessManager.AccessEvent> blocked = allEvents.stream()
                .filter(FileAccessManager.AccessEvent::conflict).toList();

        if (blocked.isEmpty()) return;

        conflictReportBox.getChildren().clear();
        conflictReportBox.getStyleClass().add("card");
        conflictReportBox.setPadding(new Insets(14));

        Label title = new Label("Resumen de bloqueos");
        title.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; -fx-font-weight: bold;");

        conflictReportBox.getChildren().addAll(title, new Separator());

        for (int i = 0; i < blocked.size(); i++) {
            FileAccessManager.AccessEvent ev = blocked.get(i);

            // Extraer quien bloqueo a quien del texto del evento
            String statusText = ev.status();
            String bloqueadoPor = "";
            if (statusText.contains("—")) {
                String[] parts = statusText.split("—");
                bloqueadoPor = parts.length > 1 ? parts[1].trim() : "";
            }

            String operacion = ev.type() == AccessType.WRITE ? "escribir" : "leer";
            String descripcion = "P" + ev.pid() + " (" + ev.processName() + ") quiso " +
                operacion + " " + ev.fileName() + " pero tuvo que esperar porque " + bloqueadoPor + ".";

            VBox item = new VBox(3);
            item.setPadding(new Insets(8, 10, 8, 10));
            item.setStyle("-fx-background-color: #1a1000; -fx-border-color: #f59e0b33; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");

            Label numLbl = new Label("#" + (i + 1));
            numLbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 10px; -fx-font-weight: bold;");

            Label descLbl = new Label(descripcion);
            descLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            descLbl.setWrapText(true);

            item.getChildren().addAll(numLbl, descLbl);
            conflictReportBox.getChildren().add(item);
        }

        conflictReportBox.setVisible(true);
        conflictReportBox.setManaged(true);
    }

    /** Solicitud de acceso configurada por el usuario. */
    private record AccessRequest(int pid, String processName, String fileName, AccessType type) {}
}
