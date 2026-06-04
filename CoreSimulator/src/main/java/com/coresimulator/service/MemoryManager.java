package com.coresimulator.service;

import com.coresimulator.model.MemoryFrame;
import com.coresimulator.model.Process;

import java.util.*;

/**
 * Gestiona la memoria fisica simulada usando paginacion por demanda.
 * Soporta dos algoritmos de reemplazo de paginas: FIFO y LRU.
 *
 * FIFO expulsa la pagina que lleva mas tiempo cargada.
 * LRU expulsa la pagina que no se ha usado desde hace mas tiempo.
 */
public class MemoryManager {

    public enum ReplacementAlgorithm { FIFO, LRU }

    public record PageEvent(int tick, int pid, String processName,
                            int pageNumber, int frameId, boolean pageFault,
                            String algorithm, String details) {}

    private final int totalFrames;
    private final MemoryFrame[] frames;
    private final List<PageEvent> eventLog;
    private int currentTick;

    // Indice para buscar en O(1) si una pagina ya esta en memoria
    // Clave: "pid:numeroPagina", Valor: indice del marco
    private final Map<String, Integer> pageIndex = new HashMap<>();

    /**
     * Crea un gestor de memoria con el número de marcos indicado.
     *
     * @param totalFrames número total de marcos disponibles
     */
    public MemoryManager(int totalFrames) {
        this.totalFrames = totalFrames;
        this.frames = new MemoryFrame[totalFrames];
        for (int i = 0; i < totalFrames; i++) frames[i] = new MemoryFrame(i);
        this.eventLog = new ArrayList<>();
        this.currentTick = 0;
    }

    /** Reinicia el estado de la memoria y el registro de eventos. */
    public void reset() {
        for (MemoryFrame f : frames) f.free();
        pageIndex.clear();
        eventLog.clear();
        currentTick = 0;
    }

    private String pageKey(int pid, int pageNumber) {
        return pid + ":" + pageNumber;
    }

    /**
     * Simula el acceso a todas las páginas de los procesos usando el algoritmo dado.
     *
     * @param processes lista de procesos
     * @param algorithm algoritmo de reemplazo (FIFO o LRU)
     */
    public void simulateAll(List<Process> processes, ReplacementAlgorithm algorithm) {
        reset();
        for (Process p : processes) {
            for (int page = 0; page < p.getPagesNeeded(); page++) {
                accessPage(p.getPid(), p.getName(), page, algorithm);
                currentTick++;
            }
        }
    }

    /**
     * Accede a una página; carga la página si no está presente (page fault).
     *
     * @return `true` si hubo page fault (se cargó la página)
     */
    public boolean accessPage(int pid, String processName, int pageNumber,
                               ReplacementAlgorithm algorithm) {
        String key = pageKey(pid, pageNumber);

        if (pageIndex.containsKey(key)) {
            int frameId = pageIndex.get(key);
            frames[frameId].setLastUsedTick(currentTick);
            eventLog.add(new PageEvent(currentTick, pid, processName, pageNumber,
                    frameId, false, algorithm.name(), "Pagina en memoria (hit)"));
            return false;
        }

        MemoryFrame target = findFreeFrame();
        if (target == null) {
            target = algorithm == ReplacementAlgorithm.FIFO ? pickFIFO() : pickLRU();
            String removedKey = pageKey(target.getProcessId(), target.getPageNumber());
            pageIndex.remove(removedKey);
            eventLog.add(new PageEvent(currentTick, pid, processName, pageNumber,
                    target.getFrameId(), true, algorithm.name(),
                    String.format("Reemplazo %s: expulso P%d[Pag.%d]",
                            algorithm.name(), target.getProcessId(), target.getPageNumber())));
        } else {
            eventLog.add(new PageEvent(currentTick, pid, processName, pageNumber,
                    target.getFrameId(), true, algorithm.name(),
                    "Pagina cargada en marco libre"));
        }

        target.load(pageNumber, pid, processName, currentTick);
        pageIndex.put(key, target.getFrameId());
        return true;
    }

    private MemoryFrame findFreeFrame() {
        for (MemoryFrame f : frames) if (f.isFree()) return f;
        return null;
    }

    private MemoryFrame pickFIFO() {
        return Arrays.stream(frames)
                .min(Comparator.comparingInt(MemoryFrame::getLoadedAtTick))
                .orElse(frames[0]);
    }

    private MemoryFrame pickLRU() {
        return Arrays.stream(frames)
                .min(Comparator.comparingInt(MemoryFrame::getLastUsedTick))
                .orElse(frames[0]);
    }

    /** Devuelve el array de marcos actual. */
    public MemoryFrame[] getFrames()      { return frames; }

    /** Devuelve una copia del log de eventos de página. */
    public List<PageEvent> getEventLog()  { return new ArrayList<>(eventLog); }

    /** Número total de marcos. */
    public int getTotalFrames()           { return totalFrames; }

    /** Cuenta de page faults registrados. */
    public long getPageFaultCount() {
        return eventLog.stream().filter(PageEvent::pageFault).count();
    }
}
