package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.Process.ProcessState;
import com.coresimulator.model.SchedulerResult;
import com.coresimulator.model.SchedulerResult.GanttEntry;

import java.util.*;

/**
 * Implementacion del algoritmo de planificacion por Prioridad (no-expropiativo).
 * La prioridad 1 es la mas alta. Incluye mecanismo de Aging para evitar
 * que los procesos de baja prioridad esperen indefinidamente.
 */
public class PriorityScheduler implements SchedulerService {

    private static final int AGING_INTERVAL = 5;  // ticks en cola antes de mejorar prioridad

    /** Nombre legible del algoritmo. */
    @Override
    public String getAlgorithmName() { return "Planificacion por Prioridad (con Aging)"; }

    /** Ejecuta la simulación por prioridad (no expropiativa). */
    @Override
    public SchedulerResult simulate(List<Process> original) {
        List<Process> processes = copyProcessList(original);
        processes.sort(Comparator.comparingInt(Process::getArrivalTime));

        List<Process> readyQueue = new ArrayList<>();
        List<GanttEntry> gantt = new ArrayList<>();
        List<Process> finished = new ArrayList<>();
        Map<Process, Integer> ticksInQueue = new HashMap<>();

        int currentTick = 0;
        int nextArrivalIndex = 0;

        while (finished.size() < processes.size()) {
            while (nextArrivalIndex < processes.size()
                    && processes.get(nextArrivalIndex).getArrivalTime() <= currentTick) {
                Process p = processes.get(nextArrivalIndex++);
                p.setState(ProcessState.READY);
                readyQueue.add(p);
                ticksInQueue.put(p, 0);
            }

            if (readyQueue.isEmpty()) {
                if (nextArrivalIndex < processes.size()) {
                    int nextTick = processes.get(nextArrivalIndex).getArrivalTime();
                    gantt.add(new GanttEntry(-1, "IDLE", currentTick, nextTick));
                    currentTick = nextTick;
                }
                continue;
            }

            // Aging: mejorar prioridad de procesos que llevan mucho tiempo esperando
            for (Process p : readyQueue) {
                int waited = ticksInQueue.get(p) + 1;
                ticksInQueue.put(p, waited);
                if (waited > 0 && waited % AGING_INTERVAL == 0 && p.priorityProperty().get() > 1) {
                    p.priorityProperty().set(p.priorityProperty().get() - 1);
                }
            }

            readyQueue.sort(Comparator.comparingInt(Process::getPriority));
            Process current = readyQueue.remove(0);
            ticksInQueue.remove(current);
            current.setState(ProcessState.RUNNING);

            int sliceStart = currentTick;
            currentTick += current.getTimeLeft();

            gantt.add(new GanttEntry(current.getPid(), current.getName(), sliceStart, currentTick));

            for (Process waiting : readyQueue) {
                waiting.setWaitTime(waiting.getWaitTime() + (currentTick - sliceStart));
            }

            current.setTimeLeft(0);
            current.setState(ProcessState.TERMINATED);
            current.setCompletionTime(currentTick - current.getArrivalTime());
            current.setWaitTime(sliceStart - current.getArrivalTime());
            finished.add(current);

            while (nextArrivalIndex < processes.size()
                    && processes.get(nextArrivalIndex).getArrivalTime() <= currentTick) {
                Process p = processes.get(nextArrivalIndex++);
                p.setState(ProcessState.READY);
                readyQueue.add(p);
                ticksInQueue.put(p, 0);
            }
        }

        return buildResult(gantt, finished, currentTick);
    }

    private SchedulerResult buildResult(List<GanttEntry> gantt, List<Process> finished, int totalTime) {
        double avgWait  = finished.stream().mapToInt(Process::getWaitTime).average().orElse(0);
        double avgTA    = finished.stream().mapToInt(Process::getCompletionTime).average().orElse(0);
        int busyTicks   = gantt.stream().filter(e -> e.pid() != -1).mapToInt(e -> e.endTime() - e.startTime()).sum();
        double cpuUsage = totalTime > 0 ? (busyTicks * 100.0 / totalTime) : 0;
        return new SchedulerResult(gantt, avgWait, avgTA, cpuUsage, finished);
    }

    private List<Process> copyProcessList(List<Process> source) {
        List<Process> copy = new ArrayList<>();
        for (Process p : source) {
            copy.add(new Process(p.getPid(), p.getName(), p.getPriority(),
                    p.getCpuDuration(), p.getArrivalTime(), p.isUsesFile(), p.getPagesNeeded()));
        }
        return copy;
    }
}
