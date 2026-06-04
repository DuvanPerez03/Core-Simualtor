package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.SchedulerResult;
import java.util.List;

/**
 * Interfaz comun para los algoritmos de planificacion de procesos.
 * Cada algoritmo recibe la lista de procesos y devuelve el resultado
 * con el diagrama de Gantt y las metricas calculadas.
 */
public interface SchedulerService {
    /** Ejecuta la simulación sobre la lista de procesos y devuelve resultados. */
    SchedulerResult simulate(List<Process> processes);

    /** Nombre legible del algoritmo implementado. */
    String getAlgorithmName();
}
