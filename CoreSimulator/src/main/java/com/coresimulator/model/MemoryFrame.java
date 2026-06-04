package com.coresimulator.model;

/**
 * Representa un marco de pagina en la memoria fisica simulada.
 * Cada marco puede estar libre o contener una pagina de algun proceso.
 */
/**
 * Representa un marco de página en la memoria física simulada.
 */
public class MemoryFrame {

    private int frameId;
    private int pageNumber;    // -1 indica que el marco esta libre
    private int processId;     // -1 indica que el marco esta libre
    private String processName;
    private int loadedAtTick;  // tick en que se cargo la pagina (usado por FIFO)
    private int lastUsedTick;  // ultimo tick de acceso (usado por LRU)

    /**
     * Crea un marco con id y estado libre.
     *
     * @param frameId identificador del marco
     */
    public MemoryFrame(int frameId) {
        this.frameId = frameId;
        this.pageNumber = -1;
        this.processId = -1;
        this.processName = "";
        this.loadedAtTick = 0;
        this.lastUsedTick = 0;
    }

    /** Indica si el marco está libre. */
    public boolean isFree() { return pageNumber == -1; }

    /** Carga una página en el marco con metadatos de tiempo. */
    public void load(int pageNumber, int processId, String processName, int currentTick) {
        this.pageNumber = pageNumber;
        this.processId = processId;
        this.processName = processName;
        this.loadedAtTick = currentTick;
        this.lastUsedTick = currentTick;
    }

    /** Libera el marco (marca como vacío). */
    public void free() {
        this.pageNumber = -1;
        this.processId = -1;
        this.processName = "";
    }

    public int getFrameId()      { return frameId; }
    public int getPageNumber()   { return pageNumber; }
    public int getProcessId()    { return processId; }
    public int getProcessPid()   { return processId; }
    public String getProcessName(){ return processName; }
    public int getLoadedAtTick() { return loadedAtTick; }
    public int getLoadTime()     { return loadedAtTick; }
    public int getLastUsedTick() { return lastUsedTick; }
    public int getLastUsedTime() { return lastUsedTick; }
    public void setLastUsedTick(int t) { this.lastUsedTick = t; }
    public void setLastUsedTime(int t) { this.lastUsedTick = t; }
}
