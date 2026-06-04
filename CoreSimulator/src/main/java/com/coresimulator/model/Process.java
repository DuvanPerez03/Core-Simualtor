package com.coresimulator.model;

import javafx.beans.property.*;

/**
 * Representa un proceso dentro del simulador.
 * Almacena todos los atributos necesarios para los algoritmos
 * de planificacion, gestion de memoria y acceso a archivos.
 */
/**
 * Representa un proceso dentro del simulador.
 * Almacena atributos usados por los algoritmos de planificación y la UI.
 */
public class Process {

    public enum ProcessState { NEW, READY, RUNNING, WAITING, TERMINATED }

    private final IntegerProperty id             = new SimpleIntegerProperty();
    private final StringProperty  name           = new SimpleStringProperty();
    private final IntegerProperty priority       = new SimpleIntegerProperty();
    private final IntegerProperty cpuDuration    = new SimpleIntegerProperty();
    private final IntegerProperty timeLeft       = new SimpleIntegerProperty();
    private final IntegerProperty waitTime       = new SimpleIntegerProperty();
    private final IntegerProperty arrivalTime    = new SimpleIntegerProperty();
    private final IntegerProperty completionTime = new SimpleIntegerProperty();
    private final ObjectProperty<ProcessState> state = new SimpleObjectProperty<>(ProcessState.NEW);
    private final BooleanProperty accessesFiles  = new SimpleBooleanProperty();
    private final IntegerProperty pagesNeeded    = new SimpleIntegerProperty();

    /**
     * Construye un proceso con los atributos básicos.
     *
     * @param id          identificador del proceso
     * @param name        nombre legible
     * @param priority    prioridad (1 = más alta)
     * @param cpuDuration duración total de CPU (burst)
     * @param arrivalTime tiempo de llegada
     * @param accessesFiles si accede a archivos
     * @param pagesNeeded número de páginas de memoria requeridas
     */
    public Process(int id, String name, int priority, int cpuDuration,
                   int arrivalTime, boolean accessesFiles, int pagesNeeded) {
        this.id.set(id);
        this.name.set(name);
        this.priority.set(priority);
        this.cpuDuration.set(cpuDuration);
        this.timeLeft.set(cpuDuration);
        this.arrivalTime.set(arrivalTime);
        this.waitTime.set(0);
        this.completionTime.set(0);
        this.accessesFiles.set(accessesFiles);
        this.pagesNeeded.set(pagesNeeded);
        this.state.set(ProcessState.NEW);
    }

    public int getId()                { return id.get(); }
    public IntegerProperty idProperty() { return id; }

    public String getName()              { return name.get(); }
    public StringProperty nameProperty() { return name; }

    public int getPriority()                  { return priority.get(); }
    public IntegerProperty priorityProperty() { return priority; }

    public int getCpuDuration()                  { return cpuDuration.get(); }
    public IntegerProperty cpuDurationProperty() { return cpuDuration; }

    public int getTimeLeft()                  { return timeLeft.get(); }
    public void setTimeLeft(int t)            { timeLeft.set(t); }
    public IntegerProperty timeLeftProperty() { return timeLeft; }

    public int getWaitTime()                  { return waitTime.get(); }
    public void setWaitTime(int t)            { waitTime.set(t); }
    public IntegerProperty waitTimeProperty() { return waitTime; }

    public int getArrivalTime()                  { return arrivalTime.get(); }
    public IntegerProperty arrivalTimeProperty() { return arrivalTime; }

    public int getCompletionTime()                  { return completionTime.get(); }
    public void setCompletionTime(int t)            { completionTime.set(t); }
    public IntegerProperty completionTimeProperty() { return completionTime; }

    public ProcessState getState()       { return state.get(); }
    public void setState(ProcessState s) { state.set(s); }
    public ObjectProperty<ProcessState> stateProperty() { return state; }

    public boolean accessesFiles()                { return accessesFiles.get(); }
    public BooleanProperty accessesFilesProperty(){ return accessesFiles; }

    // Aliases para compatibilidad con vistas y servicios
    public int getPid()              { return id.get(); }
    public int getBurstTime()        { return cpuDuration.get(); }
    public int getMemoryPages()      { return pagesNeeded.get(); }
    public int getWaitingTime()      { return waitTime.get(); }
    public void setWaitingTime(int t){ waitTime.set(t); }
    public int getTurnaroundTime()   { return completionTime.get(); }
    public void setTurnaroundTime(int t){ completionTime.set(t); }
    public int getRemainingTime()    { return timeLeft.get(); }
    public void setRemainingTime(int t){ timeLeft.set(t); }
    public boolean isUsesFile()      { return accessesFiles.get(); }
    public int getPagesNeeded()      { return pagesNeeded.get(); }

    public IntegerProperty pidProperty()            { return id; }
    public IntegerProperty burstTimeProperty()      { return cpuDuration; }
    public IntegerProperty memoryPagesProperty()    { return pagesNeeded; }
    public IntegerProperty waitingTimeProperty()    { return waitTime; }
    public IntegerProperty turnaroundTimeProperty() { return completionTime; }

    @Override
    public String toString() {
        return String.format("P%d[%s]", getId(), getName());
    }
}
