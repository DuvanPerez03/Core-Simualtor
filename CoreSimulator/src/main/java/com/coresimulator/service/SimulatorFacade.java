package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.SchedulerResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Punto central de acceso a todos los servicios del simulador.
 * Las vistas solo interactuan con esta clase; nunca con los
 * servicios directamente. Esto simplifica el flujo de datos
 * y facilita agregar nuevas funciones sin tocar la interfaz grafica.
 */
public class SimulatorFacade {

    private List<Process> processList;
    private MemoryManager memoryManager;
    private final FileAccessManager fileAccessManager;

    private int roundRobinQuantum = 2;

    /**
     * Fachada central que agrupa servicios del simulador.
     *
     * @param initialFrameCount número inicial de marcos de memoria
     */
    public SimulatorFacade(int initialFrameCount) {
        this.processList      = new ArrayList<>();
        this.memoryManager    = new MemoryManager(initialFrameCount);
        this.fileAccessManager = new FileAccessManager();
        addDefaultFiles();
    }

    private void addDefaultFiles() {
        fileAccessManager.addFile("datos.txt");
        fileAccessManager.addFile("config.sys");
        fileAccessManager.addFile("log.txt");
    }

    // --- Procesos ---

    /** Añade un proceso al simulador. */
    public void addProcess(Process p)     { processList.add(p); }

    /** Elimina un proceso del simulador. */
    public void removeProcess(Process p)  { processList.remove(p); }

    /** Borra todos los procesos. */
    public void clearProcesses()          { processList.clear(); }

    /** Devuelve una copia de la lista de procesos. */
    public List<Process> getProcessList() { return new ArrayList<>(processList); }

    /** Carga un conjunto de procesos de ejemplo en la lista. */
    public void loadSampleProcesses() {
        clearProcesses();
        addProcess(new Process(1, "Editor",     2, 6, 0, false, 3));
        addProcess(new Process(2, "Compilador", 1, 8, 1, true,  5));
        addProcess(new Process(3, "Navegador",  3, 4, 2, false, 2));
        addProcess(new Process(4, "Base Datos", 1, 9, 3, true,  6));
        addProcess(new Process(5, "Antivirus",  4, 3, 4, false, 2));
        addProcess(new Process(6, "Servidor",   2, 7, 5, true,  4));
    }

    // --- Planificacion ---

    /** Ejecuta Round Robin con el quantum configurado. */
    public SchedulerResult runRoundRobin() {
        return new RoundRobinScheduler(roundRobinQuantum).simulate(processList);
    }

    /** Ejecuta SJF (Shortest Job First). */
    public SchedulerResult runSJF() {
        return new SJFScheduler().simulate(processList);
    }

    /** Ejecuta el algoritmo por Prioridad. */
    public SchedulerResult runPriority() {
        return new PriorityScheduler().simulate(processList);
    }

    public void setRrQuantum(int q)  { this.roundRobinQuantum = q; }
    public int  getRrQuantum()       { return roundRobinQuantum; }

    // --- Memoria ---

    /** Simula la actividad de memoria usando FIFO. */
    public void simulateMemoryFIFO() {
        memoryManager.simulateAll(processList, MemoryManager.ReplacementAlgorithm.FIFO);
    }

    /** Simula la actividad de memoria usando LRU. */
    public void simulateMemoryLRU() {
        memoryManager.simulateAll(processList, MemoryManager.ReplacementAlgorithm.LRU);
    }

    /** Reconstruye el MemoryManager con un nuevo número de marcos. */
    public void rebuildMemoryManager(int frameCount) {
        this.memoryManager = new MemoryManager(frameCount);
    }

    /** Devuelve el MemoryManager activo. */
    public MemoryManager getMemoryManager() { return memoryManager; }

    // Alias para compatibilidad con vistas que llaman getMemoryService()
    public MemoryManager getMemoryService() { return memoryManager; }
    public void rebuildMemoryService(int frameCount) { rebuildMemoryManager(frameCount); }

    // --- Archivos ---

    /** Simula el acceso concurrente a archivos según la lista de procesos. */
    public void simulateFileAccess() {
        fileAccessManager.simulateConcurrentAccess(processList);
    }

    /** Devuelve el servicio de acceso a archivos. */
    public FileAccessManager getFileService() { return fileAccessManager; }
}
