package com.coresimulator.service;

import com.coresimulator.model.Process;
import com.coresimulator.model.SharedFile;
import com.coresimulator.model.SharedFile.AccessType;

import java.util.*;
import java.util.concurrent.*;

/**
 * Gestiona el acceso concurrente de varios procesos a archivos compartidos.
 * Usa un pool de hilos para simular verdadera concurrencia y semaforos
 * dentro de SharedFile para controlar los conflictos.
 */
public class FileAccessManager {

    public record AccessEvent(int tick, int pid, String processName,
                              String fileName, AccessType type,
                              boolean conflict, String status) {}

    private final Map<String, SharedFile> sharedFiles;
    private final List<AccessEvent> eventLog;
    private final Object logLock = new Object();

    /** Crea un gestor de accesos a archivos compartidos. */
    public FileAccessManager() {
        this.sharedFiles = new LinkedHashMap<>();
        this.eventLog = new ArrayList<>();
    }

    /** Añade un archivo ficticio al conjunto de archivos compartidos. */
    public void addFile(String name) {
        sharedFiles.put(name, new SharedFile(name));
    }

    /** Reinicia el estado: borra historiales y el log de eventos. */
    public void reset() {
        sharedFiles.values().forEach(SharedFile::clearHistory);
        synchronized (logLock) {
            eventLog.clear();
        }
    }

    /**
     * Simula el acceso concurrente de todos los procesos que usan archivos.
     * Cada proceso corre en su propio hilo para representar la concurrencia real.
     */
    /**
     * Simula accesos concurrentes de procesos a archivos usando un pool de hilos.
     *
     * @param processes lista de procesos del simulador
     */
    public void simulateConcurrentAccess(List<Process> processes) {
        reset();

        if (sharedFiles.isEmpty()) {
            sharedFiles.put("datos.txt",  new SharedFile("datos.txt"));
            sharedFiles.put("config.sys", new SharedFile("config.sys"));
            sharedFiles.put("log.txt",    new SharedFile("log.txt"));
        }

        List<Process> fileUsers = processes.stream()
                .filter(Process::isUsesFile).toList();

        if (fileUsers.isEmpty()) return;

        List<String> fileNames = new ArrayList<>(sharedFiles.keySet());
        Random rand = new Random(42);

        int threadCount = Math.min(4, fileUsers.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> tasks = new ArrayList<>();

        for (int i = 0; i < fileUsers.size(); i++) {
            final int tick = i;
            Process proc = fileUsers.get(i);

            tasks.add(executor.submit(() -> {
                String targetFile = fileNames.get(rand.nextInt(fileNames.size()));
                SharedFile file = sharedFiles.get(targetFile);
                AccessType type = rand.nextBoolean() ? AccessType.WRITE : AccessType.READ;

                boolean granted = file.tryAccess(proc.getPid(), proc.getName(), type, tick);

                synchronized (logLock) {
                    eventLog.add(new AccessEvent(tick, proc.getPid(), proc.getName(),
                            targetFile, type, !granted,
                            granted ? "Acceso concedido" : "Bloqueado por mutex"));
                }

                if (granted) {
                    if (type == AccessType.WRITE) {
                        // Simula tiempo de escritura y libera el mutex
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        file.releaseAccess(proc.getPid(), tick + 1);
                        synchronized (logLock) {
                            eventLog.add(new AccessEvent(tick + 1, proc.getPid(), proc.getName(),
                                    targetFile, type, false, "Mutex liberado (escritura)"));
                        }
                    } else {
                        // Simula tiempo de lectura y libera (lectores-escritores)
                        try { Thread.sleep(30); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        file.releaseAccess(proc.getPid(), tick + 1);
                        synchronized (logLock) {
                            eventLog.add(new AccessEvent(tick + 1, proc.getPid(), proc.getName(),
                                    targetFile, type, false, "Lectura finalizada"));
                        }
                    }
                } else {
                    // Bloqueado: esperar y reintentar una vez
                    try { Thread.sleep(80); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    boolean retried = file.tryAccess(proc.getPid(), proc.getName(), type, tick + 2);
                    synchronized (logLock) {
                        eventLog.add(new AccessEvent(tick + 2, proc.getPid(), proc.getName(),
                                targetFile, type, !retried,
                                retried ? "Acceso concedido (reintento)" : "Sigue bloqueado"));
                    }
                    if (retried) {
                        int sleepMs = (type == AccessType.WRITE) ? 50 : 30;
                        try { Thread.sleep(sleepMs); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        file.releaseAccess(proc.getPid(), tick + 3);
                        synchronized (logLock) {
                            String msg = (type == AccessType.WRITE)
                                    ? "Mutex liberado (reintento)"
                                    : "Lectura finalizada (reintento)";
                            eventLog.add(new AccessEvent(tick + 3, proc.getPid(), proc.getName(),
                                    targetFile, type, false, msg));
                        }
                    }
                }
            }));
        }

        for (Future<?> task : tasks) {
            try { task.get(); } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Devuelve un mapa inmutable de archivos compartidos. */
    public Map<String, SharedFile> getFiles() { return Collections.unmodifiableMap(sharedFiles); }

    /** Devuelve una copia del log de eventos de acceso. */
    public List<AccessEvent> getEventLog() {
        synchronized (logLock) { return new ArrayList<>(eventLog); }
    }

    /** Número de eventos que fueron conflictos (bloqueos). */
    public long getConflictCount() {
        synchronized (logLock) {
            return eventLog.stream().filter(AccessEvent::conflict).count();
        }
    }
}
