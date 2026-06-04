package com.coresimulator.view;

import com.coresimulator.model.Process;
import com.coresimulator.service.SimulatorFacade;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Vista de gestión de procesos.
 * Capa: View (Presentación)
 */
public class ProcessView extends BorderPane {

    private final SimulatorFacade facade;
    private final ObservableList<Process> processos;
    private TableView<Process> tableView;

    // Form fields
    private TextField tfName;
    private Spinner<Integer> spPriority, spBurst, spArrival, spPages;
    private CheckBox cbUsesFile;

    /**
     * Construye la vista de gestión de procesos.
     *
     * @param facade fachada de servicios del simulador
     */
    public ProcessView(SimulatorFacade facade) {
        this.facade = facade;
        this.processos = FXCollections.observableArrayList(facade.getProcessList());
        build();
    }

    private void build() {
        setStyle("-fx-background-color: transparent;");
        setPadding(new Insets(0));

        // Header
        VBox header = new VBox(4);
        Label title = new Label("🖥  Gestión de Procesos");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Crea y administra los procesos que serán planificados por el simulador");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Content split
        HBox content = new HBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        // Left: Table
        VBox tableBox = buildTableSection();
        HBox.setHgrow(tableBox, Priority.ALWAYS);

        // Right: Form
        VBox formBox = buildFormSection();
        formBox.setMinWidth(280);
        formBox.setMaxWidth(280);

        content.getChildren().addAll(tableBox, formBox);

        VBox main = new VBox(0, header, content);
        main.setPadding(new Insets(0));
        setCenter(main);
    }

    private VBox buildTableSection() {
        VBox box = new VBox(12);

        // Toolbar
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button btnSample = new Button("Cargar Ejemplo");
        btnSample.getStyleClass().add("btn-secondary");

        Button btnClear = new Button("🗑 Limpiar Todo");
        btnClear.getStyleClass().add("btn-danger");

        Label countLabel = new Label();
        countLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(btnSample, btnClear, spacer, countLabel);

        // Table
        tableView = new TableView<>(processos);
        tableView.getStyleClass().add("table-view");
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setPlaceholder(new Label("Sin procesos. Añade uno o carga el ejemplo."));

        TableColumn<Process, Integer> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPid.setMaxWidth(50);

        TableColumn<Process, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Process, Integer> colPriority = new TableColumn<>("Prioridad");
        colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
        colPriority.setMaxWidth(70);

        TableColumn<Process, Integer> colBurst = new TableColumn<>("Duracion CPU");
        colBurst.setCellValueFactory(new PropertyValueFactory<>("cpuDuration"));
        colBurst.setMaxWidth(70);

        TableColumn<Process, Integer> colArrival = new TableColumn<>("Llegada");
        colArrival.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));
        colArrival.setMaxWidth(70);

        TableColumn<Process, Integer> colPages = new TableColumn<>("Paginas RAM");
        colPages.setCellValueFactory(new PropertyValueFactory<>("pagesNeeded"));
        colPages.setMaxWidth(70);

        TableColumn<Process, String> colFile = new TableColumn<>("Archivo");
        colFile.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().isUsesFile() ? "✓" : "—"));
        colFile.setMaxWidth(60);
        colFile.setStyle("-fx-alignment: CENTER;");

        TableColumn<Process, String> colState = new TableColumn<>("Estado");
        colState.setCellValueFactory(cd ->
                new SimpleStringProperty(stateLabel(cd.getValue().getState())));
        colState.setMaxWidth(90);

        // Action column
        TableColumn<Process, Void> colDelete = new TableColumn<>("Acción");
        colDelete.setMaxWidth(70);
        colDelete.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-background-color: #450a0a; -fx-text-fill: #ef4444; " +
                        "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 2 8;");
                btn.setOnAction(e -> {
                    Process p = getTableView().getItems().get(getIndex());
                    facade.removeProcess(p);
                    refresh();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableView.getColumns().addAll(colPid, colName, colPriority, colBurst, colArrival, colPages, colFile, colState, colDelete);

        // Update count
        processos.addListener((javafx.collections.ListChangeListener<Process>) c ->
                countLabel.setText(processos.size() + " proceso(s)"));
        countLabel.setText(processos.size() + " proceso(s)");

        btnSample.setOnAction(e -> { facade.loadSampleProcesses(); refresh(); });
        btnClear.setOnAction(e -> { facade.clearProcesses(); refresh(); });

        VBox card = new VBox(12, toolbar, tableView);
        card.getStyleClass().add("card");
        VBox.setVgrow(tableView, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);

        box.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);

        return box;
    }

    private VBox buildFormSection() {
        VBox box = new VBox(0);

        Label formTitle = new Label("➕  Nuevo Proceso");
        formTitle.getStyleClass().add("section-title");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        // Name
        VBox nameGroup = createField("Nombre del proceso");
        tfName = new TextField();
        tfName.getStyleClass().add("text-input");
        tfName.setPromptText("ej. MiProceso");
        nameGroup.getChildren().add(tfName);

        // Priority
        VBox prioGroup = createField("Prioridad (1 = alta)");
        spPriority = new Spinner<>(1, 10, 2);
        styleSpinner(spPriority);
        prioGroup.getChildren().add(spPriority);

        // Burst
        VBox burstGroup = createField("Duración en CPU");
        spBurst = new Spinner<>(1, 30, 5);
        styleSpinner(spBurst);
        burstGroup.getChildren().add(spBurst);

        // Arrival
        VBox arrivalGroup = createField("Tiempo de llegada");
        spArrival = new Spinner<>(0, 20, 0);
        styleSpinner(spArrival);
        arrivalGroup.getChildren().add(spArrival);

        // Memory pages
        VBox pagesGroup = createField("Páginas de memoria requeridas");
        spPages = new Spinner<>(1, 10, 3);
        styleSpinner(spPages);
        pagesGroup.getChildren().add(spPages);

        // Uses file
        cbUsesFile = new CheckBox("  Accede a archivos");
        cbUsesFile.getStyleClass().add("check-box");
        cbUsesFile.setStyle("-fx-text-fill: #94a3b8;");

        Button btnAdd = new Button("  Agregar Proceso");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setOnAction(e -> addProcess());

        form.getChildren().addAll(nameGroup, prioGroup, burstGroup, arrivalGroup, pagesGroup, cbUsesFile, btnAdd);

        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        // Info box
        VBox info = new VBox(4);
        info.getStyleClass().add("info-box");
        Label infoText = new Label("💡 La prioridad 1 es la más alta. Los procesos con acceso a archivo participarán en la simulación de mutex.");
        infoText.getStyleClass().add("info-box-text");
        infoText.setWrapText(true);
        info.getChildren().add(infoText);

        card.getChildren().addAll(formTitle, new Separator(), form, info);
        box.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);

        return box;
    }

    private VBox createField(String labelText) {
        VBox group = new VBox(4);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("input-label");
        group.getChildren().add(lbl);
        return group;
    }

    private void styleSpinner(Spinner<?> spinner) {
        spinner.setMaxWidth(Double.MAX_VALUE);
        spinner.getStyleClass().add("spinner");
        spinner.setEditable(true);
    }

    private void addProcess() {
        String name = tfName.getText().trim();
        if (name.isEmpty()) {
            showAlert("El nombre del proceso no puede estar vacío.");
            return;
        }
        int nextPid = processos.isEmpty() ? 1 :
                processos.stream().mapToInt(Process::getPid).max().orElse(0) + 1;

        Process p = new Process(
                nextPid,
                name,
                spPriority.getValue(),
                spBurst.getValue(),
                spArrival.getValue(),
                cbUsesFile.isSelected(),
                spPages.getValue()
        );
        facade.addProcess(p);
        tfName.clear();
        refresh();
    }

    private void refresh() {
        processos.setAll(facade.getProcessList());
    }

    private String stateLabel(Process.ProcessState state) {
        return switch (state) {
            case NEW        -> "Nuevo";
            case READY      -> "Listo";
            case RUNNING    -> "Ejecutando";
            case WAITING    -> "Esperando";
            case TERMINATED -> "Terminado";
        };
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Atención");
        alert.showAndWait();
    }
}
