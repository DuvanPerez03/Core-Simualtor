package com.coresimulator.controller;

import com.coresimulator.service.SimulatorFacade;
import com.coresimulator.view.*;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

/**
 * Controlador principal de la aplicación.
 *
 * <p>Se encarga de construir la interfaz principal (barra lateral y área de contenido),
 * gestionar la navegación entre las vistas y requisitar las vistas bajo demanda.
 *
 * <p>Documentación orientada a mantenimiento: los componentes principales se crean de
 * manera perezosa (lazy) y se reponen cuando una vista debe refrescar su contenido.
 */
public class MainController {

    /** Nodo raíz de la interfaz que contiene la barra lateral y el contenido principal. */
    private final BorderPane root;

    /** Fachada que expone servicios del simulador (gestión de memoria, procesos, etc.). */
    private final SimulatorFacade facade;

    /** Botones de navegación de la barra lateral. */
    private Button btnDashboard, btnProcesses, btnScheduler, btnMemory, btnFiles;

    /** Referencia al botón actualmente activo (resaltado). */
    private Button currentActive;

    /** Vistas generadas bajo demanda. */
    private DashboardView dashboardView;
    private ProcessView processView;
    private SchedulerView schedulerView;
    private MemoryView memoryView;
    private FileView fileView;

    /**
     * Construye el controlador principal y prepara la interfaz.
     *
     * <p>Inicializa la fachada del simulador con un tamaño de espacio de marcos y
     * crea el nodo raíz de la UI.
     */
    public MainController() {
        this.facade = new SimulatorFacade(16);
        this.root = new BorderPane();
        build();
    }

    /** Construye la estructura inicial de la UI: estilo, sidebar y vista por defecto. */
    private void build() {
        root.setStyle("-fx-background-color: #0d0f14;");

        VBox sidebar = buildSidebar();
        root.setLeft(sidebar);

        navigateTo("dashboard");
    }

    /**
     * Crea la barra lateral con logo, secciones de navegación y área inferior.
     *
     * @return una instancia de `VBox` que actúa como sidebar
     */
    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("sidebar");

        VBox logoArea = new VBox(2);
        logoArea.setPadding(new Insets(24, 16, 16, 16));
        logoArea.setStyle("-fx-border-color: transparent transparent #1e2d3d transparent; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("⚡ Core Simulator");
        logo.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 17px; -fx-font-weight: bold;");
        logoArea.getChildren().addAll(logo);

        VBox navSection = new VBox(2);
        navSection.setPadding(new Insets(16, 0, 0, 0));

        Label navLabel = new Label("NAVEGACIÓN");
        navLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0 0 8 20;");

        btnDashboard = navButton(" Inicio", "dashboard");
        btnProcesses = navButton("  Procesos", "processes");
        btnScheduler = navButton(" Planificador", "scheduler");
        btnMemory = navButton(" Memoria", "memory");
        btnFiles = navButton("  Archivos", "files");

        navSection.getChildren().addAll(navLabel, btnDashboard, btnProcesses, btnScheduler, btnMemory, btnFiles);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox bottomInfo = new VBox(4);
        bottomInfo.setPadding(new Insets(16));
        bottomInfo.setStyle("-fx-border-color: #1e2d3d transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        sidebar.getChildren().addAll(logoArea, navSection, spacer, bottomInfo);
        return sidebar;
    }

    /**
     * Crea un botón de navegación con comportamiento por defecto.
     *
     * @param text texto que muestra el botón
     * @param target identificador de la vista a la que navega
     * @return botón configurado
     */
    private Button navButton(String text, String target) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> navigateTo(target));
        return btn;
    }

    /**
     * Navega a la vista indicada por `target`.
     *
     * <p>Gestiona el estado del botón activo, crea la vista bajo demanda y
     * coloca el contenido dentro de un `ScrollPane` central.
     *
     * @param target identificador de la vista ("dashboard", "processes", "scheduler", "memory", "files")
     */
    public void navigateTo(String target) {
        if (currentActive != null) {
            currentActive.getStyleClass().remove("nav-button-active");
        }

        Button newActive = switch (target) {
            case "processes" -> btnProcesses;
            case "scheduler" -> btnScheduler;
            case "memory" -> btnMemory;
            case "files" -> btnFiles;
            default -> btnDashboard;
        };
        newActive.getStyleClass().add("nav-button-active");
        currentActive = newActive;

        Region view = switch (target) {
            case "processes" -> getProcessView();
            case "scheduler" -> getSchedulerView();
            case "memory" -> getMemoryView();
            case "files" -> getFileView();
            default -> getDashboardView();
        };

        StackPane contentWrapper = new StackPane(view);
        contentWrapper.setPadding(new Insets(24));
        contentWrapper.setStyle("-fx-background-color: #0d0f14;");
        StackPane.setAlignment(view, javafx.geometry.Pos.TOP_LEFT);

        ScrollPane scroll = new ScrollPane(contentWrapper);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: #0d0f14; -fx-background: #0d0f14;");

        root.setCenter(scroll);
    }

    /**
     * Obtiene (o crea bajo demanda) la vista del dashboard.
     *
     * @return instancia de `DashboardView`
     */
    private DashboardView getDashboardView() {
        if (dashboardView == null) {
            dashboardView = new DashboardView(facade, this::navigateTo);
        }
        return dashboardView;
    }

    /**
     * Crea y devuelve la vista de procesos. Se recrea en cada invocación para
     * reflejar el estado actual del `facade`.
     *
     * @return instancia de `ProcessView`
     */
    private ProcessView getProcessView() {
        processView = new ProcessView(facade);
        return processView;
    }

    /**
     * Crea y devuelve la vista del planificador.
     *
     * @return instancia de `SchedulerView`
     */
    private SchedulerView getSchedulerView() {
        schedulerView = new SchedulerView(facade);
        return schedulerView;
    }

    /**
     * Crea y devuelve la vista de memoria.
     *
     * @return instancia de `MemoryView`
     */
    private MemoryView getMemoryView() {
        memoryView = new MemoryView(facade);
        return memoryView;
    }

    /**
     * Crea y devuelve la vista de archivos.
     *
     * @return instancia de `FileView`
     */
    private FileView getFileView() {
        fileView = new FileView(facade);
        return fileView;
    }

    /**
     * Devuelve el nodo raíz construido por este controlador.
     *
     * @return `BorderPane` raíz de la UI
     */
    public BorderPane getRoot() {
        return root;
    }
}
