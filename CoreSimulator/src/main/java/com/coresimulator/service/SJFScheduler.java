package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.Process.ProcessState;
import com.coresimulator.model.SchedulerResult;
import com.coresimulator.model.SchedulerResult.GanttEntry;

import java.util.*;

/**
 * Implementacion del algoritmo Shortest Job First (no-expropiativo).
 * De todos los procesos listos, elige el que tenga la rafaga de CPU mas corta.
 */
public class SJFScheduler implements SchedulerService {

    /** Nombre legible del algoritmo. */
    @Override
    public String getAlgorithmName() { return "SJF - Shortest Job First"; }

    /** Ejecuta la simulación SJF (no expropiativa). */
    @Override
    public SchedulerResult simulate(List<Process> original) {
        List<Process> processes = copyProcessList(original);
        processes.sort(Comparator.comparingInt(Process::getArrivalTime));

        List<Process> readyQueue = new ArrayList<>();
        List<GanttEntry> gantt = new ArrayList<>();
        List<Process> finished = new ArrayList<>();

        int currentTick = 0;
        int nextArrivalIndex = 0;

        while (finished.size() < processes.size()) {
            while (nextArrivalIndex < processes.size()
                    && processes.get(nextArrivalIndex).getArrivalTime() <= currentTick) {
                Process p = processes.get(nextArrivalIndex++);
                p.setState(ProcessState.READY);
                readyQueue.add(p);
            }

            if (readyQueue.isEmpty()) {
                if (nextArrivalIndex < processes.size()) {
                    int nextTick = processes.get(nextArrivalIndex).getArrivalTime();
                    gantt.add(new GanttEntry(-1, "IDLE", currentTick, nextTick));
                    currentTick = nextTick;
                }
                continue;
            }

            readyQueue.sort(Comparator.comparingInt(Process::getTimeLeft));
            Process current = readyQueue.remove(0);
            current.setState(ProcessState.RUNNING);

            int sliceStart = currentTick;
            currentTick += current.getTimeLeft();

            gantt.add(new GanttEntry(current.getPid(), current.getName(), sliceStart, currentTick));

            for (Process waiting : readyQueue) {
                waiting.setWaitTime(waiting.getWaitTime() + (currentTick - sliceStart));
            }

            current.setWaitTime(sliceStart - current.getArrivalTime());
            current.setTimeLeft(0);
            current.setState(ProcessState.TERMINATED);
            current.setCompletionTime(currentTick - current.getArrivalTime());
            finished.add(current);

            while (nextArrivalIndex < processes.size()
                    && processes.get(nextArrivalIndex).getArrivalTime() <= currentTick) {
                Process p = processes.get(nextArrivalIndex++);
                p.setState(ProcessState.READY);
                readyQueue.add(p);
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
