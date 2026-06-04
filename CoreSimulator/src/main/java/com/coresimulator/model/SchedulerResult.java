package com.coresimulator.model;

import java.util.List;

/**
 * Resultado de una simulacion de planificacion de procesos.
 * Contiene la secuencia de ejecucion (diagrama de Gantt) y las metricas calculadas.
 */
public class SchedulerResult {

    public record GanttEntry(int pid, String processName, int startTime, int endTime) {}

    private final List<GanttEntry> ganttEntries;
    private final double avgWaitTime;
    private final double avgCompletionTime;
    private final double cpuUsage;
    private final List<Process> finishedProcesses;

    /**
     * Crea un resultado de simulación con métricas ya calculadas.
     *
     * @param ganttEntries      entradas del diagrama de Gantt
     * @param avgWaitTime       tiempo de espera promedio
     * @param avgCompletionTime tiempo de retorno promedio
     * @param cpuUsage          porcentaje de uso de CPU
     * @param finishedProcesses lista de procesos terminados
     */
    public SchedulerResult(List<GanttEntry> ganttEntries,
                           double avgWaitTime,
                           double avgCompletionTime,
                           double cpuUsage,
                           List<Process> finishedProcesses) {
        this.ganttEntries = ganttEntries;
        this.avgWaitTime = avgWaitTime;
        this.avgCompletionTime = avgCompletionTime;
        this.cpuUsage = cpuUsage;
        this.finishedProcesses = finishedProcesses;
    }

    /** Devuelve las entradas del diagrama de Gantt. */
    public List<GanttEntry> getGanttChart()         { return ganttEntries; }

    /** Tiempo de espera promedio (en ticks). */
    public double getAvgWaitingTime()               { return avgWaitTime; }

    /** Tiempo de retorno promedio (turnaround). */
    public double getAvgTurnaroundTime()            { return avgCompletionTime; }

    /** Utilización de CPU en porcentaje. */
    public double getCpuUtilization()               { return cpuUsage; }

    /** Procesos que finalizaron durante la simulación. */
    public List<Process> getCompletedProcesses()    { return finishedProcesses; }
}
