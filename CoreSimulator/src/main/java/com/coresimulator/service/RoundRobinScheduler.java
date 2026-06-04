package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.Process.ProcessState;
import com.coresimulator.model.SchedulerResult;
import com.coresimulator.model.SchedulerResult.GanttEntry;

import java.util.*;

/**
 * Implementacion del algoritmo de planificacion Round Robin.
 * Cada proceso recibe un tiempo maximo de CPU (quantum) antes
 * de ceder el procesador al siguiente en la cola circular.
 */
public class RoundRobinScheduler implements SchedulerService {

    private final int quantum;

    /**
     * Crea un scheduler Round-Robin con el quantum dado.
     *
     * @param quantum tamaño de quantum en ticks
     */
    public RoundRobinScheduler(int quantum) {
        this.quantum = quantum;
    }

    /** Nombre legible del algoritmo. */
    @Override
    public String getAlgorithmName() {
        return "Round Robin (Q=" + quantum + ")";
    }

    /** Ejecuta la simulación sobre la lista de procesos y devuelve el resultado. */
    @Override
    public SchedulerResult simulate(List<Process> original) {
        List<Process> processes = copyProcessList(original);
        processes.sort(Comparator.comparingInt(Process::getArrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        List<GanttEntry> gantt = new ArrayList<>();
        List<Process> finished = new ArrayList<>();
        Set<Process> waitTimeRecorded = new HashSet<>();

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

            Process current = readyQueue.poll();
            current.setState(ProcessState.RUNNING);

            int sliceTime = Math.min(quantum, current.getTimeLeft());
            int sliceStart = currentTick;
            currentTick += sliceTime;

            gantt.add(new GanttEntry(current.getPid(), current.getName(), sliceStart, currentTick));

            if (!waitTimeRecorded.contains(current)) {
                current.setWaitTime(sliceStart - current.getArrivalTime());
                waitTimeRecorded.add(current);
            }

            for (Process waiting : readyQueue) {
                waiting.setWaitTime(waiting.getWaitTime() + sliceTime);
            }

            current.setTimeLeft(current.getTimeLeft() - sliceTime);

            while (nextArrivalIndex < processes.size()
                    && processes.get(nextArrivalIndex).getArrivalTime() <= currentTick) {
                Process p = processes.get(nextArrivalIndex++);
                p.setState(ProcessState.READY);
                readyQueue.add(p);
            }

            if (current.getTimeLeft() == 0) {
                current.setState(ProcessState.TERMINATED);
                current.setCompletionTime(currentTick - current.getArrivalTime());
                finished.add(current);
            } else {
                current.setState(ProcessState.READY);
                readyQueue.add(current);
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
