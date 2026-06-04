package com.coresimulator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Representa un archivo compartido dentro del simulador.
 * Implementa el patron Lectores-Escritores usando semaforos:
 * varios procesos pueden leer al mismo tiempo, pero solo
 * uno puede escribir y bloquea a los demas mientras lo hace.
 */
public class SharedFile {

    public enum AccessType { READ, WRITE }

    public record AccessRecord(int tick, int processId, String processName,
                               AccessType type, boolean wasBlocked, String outcome) {}

    private final String fileName;
    private volatile boolean writeLocked;
    private volatile int writingProcessId;
    private final List<AccessRecord> accessHistory;
    private final Object historyLock = new Object(); // protege accessHistory en entorno concurrente

    private final Semaphore writerLock  = new Semaphore(1);  // permite un solo escritor
    private final Semaphore readerLock  = new Semaphore(1);  // protege el contador de lectores
    private volatile int activeReaders  = 0;

    public SharedFile(String fileName) {
        this.fileName = fileName;
        this.writeLocked = false;
        this.writingProcessId = -1;
        this.accessHistory = new ArrayList<>();
    }

    /**
     * Intenta acceder al archivo para lectura o escritura.
     *
     * @param processId   id del proceso que pide acceso
     * @param processName nombre del proceso
     * @param type        tipo de acceso (`READ` o `WRITE`)
     * @param tick        instante lógico de la petición
     * @return `true` si el acceso fue concedido, `false` si fue interrumpido o bloqueado
     */
    public boolean tryAccess(int processId, String processName,
                               AccessType type, int tick) {
        if (type == AccessType.READ) {
            try {
                readerLock.acquire();
                activeReaders++;
                if (activeReaders == 1) {
                    writerLock.acquire();  // primer lector bloquea escritores
                }
                readerLock.release();
                synchronized (historyLock) {
                    accessHistory.add(new AccessRecord(tick, processId, processName,
                            type, false, "Lectura permitida"));
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (historyLock) {
                    accessHistory.add(new AccessRecord(tick, processId, processName,
                            type, true, "Lectura interrumpida"));
                }
                return false;
            }
        } else {
            try {
                writerLock.acquire();
                writeLocked = true;
                writingProcessId = processId;
                synchronized (historyLock) {
                    accessHistory.add(new AccessRecord(tick, processId, processName,
                            type, false, "Mutex adquirido (escritura)"));
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (historyLock) {
                    accessHistory.add(new AccessRecord(tick, processId, processName,
                            type, true, "Bloqueado - esperando a P" + writingProcessId));
                }
                return false;
            }
        }
    }

    /**
     * Libera un acceso previamente adquirido por `processId`.
     *
     * @param processId id del proceso que libera el recurso
     * @param tick      instante lógico en que se libera
     */
    public void releaseAccess(int processId, int tick) {
        if (writeLocked && writingProcessId == processId) {
            writeLocked = false;
            writingProcessId = -1;
            writerLock.release();
            synchronized (historyLock) {
                accessHistory.add(new AccessRecord(tick, processId, "P" + processId,
                        AccessType.WRITE, false, "Mutex liberado (escritura)"));
            }
        } else {
            try {
                readerLock.acquire();
                activeReaders--;
                if (activeReaders == 0) {
                    writerLock.release();  // ultimo lector libera escritores
                }
                readerLock.release();
                synchronized (historyLock) {
                    accessHistory.add(new AccessRecord(tick, processId, "P" + processId,
                            AccessType.READ, false, "Lectura finalizada"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Devuelve el nombre del archivo. */
    public String getFileName()              { return fileName; }

    /** Indica si hay un escritor activo. */
    public boolean isWriteLocked()           { return writeLocked; }

    /** Devuelve el PID del proceso escritor o -1 si ninguno. */
    public int getWritingProcessId()         { return writingProcessId; }

    /** Devuelve el número de lectores activos. */
    public int getActiveReaders()            { return activeReaders; }

    /** Devuelve una copia del historial de accesos. */
    public List<AccessRecord> getAccessHistory() {
        synchronized (historyLock) { return new ArrayList<>(accessHistory); }
    }

    /** Borra el historial de accesos. */
    public void clearHistory() {
        synchronized (historyLock) { accessHistory.clear(); }
    }

    // Aliases para compatibilidad con FileService original
    public boolean isLocked()     { return writeLocked; }
    public int getLockedByPid()   { return writingProcessId; }
    public List<AccessRecord> getAccessLogs() { return getAccessHistory(); }
    public void clearLogs()       { clearHistory(); }
}
