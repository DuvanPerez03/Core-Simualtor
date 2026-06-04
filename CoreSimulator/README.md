# CoreSimulator - Simulador de Sistema Operativo

Proyecto desarrollado para la asignatura Sistemas Operativos 2026-1.
Simula los tres subsistemas principales de un sistema operativo: planificacion
de procesos, gestion de memoria y control de acceso a archivos.

Implementado en Java con JavaFX para la interfaz grafica.

---

## Estructura del proyecto

    CoreSimulator/
    src/main/java/com/coresimulator/
        model/         Clases de datos (Process, MemoryFrame, SharedFile, SchedulerResult)
        service/       Logica de simulacion (planificadores, memoria, archivos, fachada)
        view/          Interfaces graficas de cada modulo
        controller/    Coordinacion entre vistas y servicios
        MainApp.java   Punto de entrada de la aplicacion

---

## Como ejecutar

Requisitos: Java 17 o superior, Maven 3.6 o superior, JavaFX 17.

    mvn clean javafx:run

---

## Modulos del simulador

### Planificacion de procesos

Se implementaron tres algoritmos:

- Round Robin: cada proceso recibe un tiempo maximo de CPU (quantum) antes
  de ceder el procesador. Util para sistemas interactivos donde todos los
  procesos deben recibir atencion periodica.

- SJF (Shortest Job First): de los procesos listos, ejecuta primero el que
  tenga la rafaga de CPU mas corta. Minimiza el tiempo de espera promedio,
  pero puede generar starvation en procesos largos.

- Prioridad con Aging: ejecuta el proceso de mayor prioridad disponible.
  Para evitar starvation, los procesos que llevan mucho tiempo esperando
  aumentan gradualmente su prioridad (aging).

Metricas registradas: tiempo de espera, tiempo de retorno (turnaround),
porcentaje de uso de CPU y diagrama de Gantt animado.

### Gestion de memoria

Simula paginacion por demanda: las paginas de cada proceso se cargan en
marcos fisicos solo cuando se acceden, no todas al inicio.

Algoritmos de reemplazo cuando no hay marcos libres:

- FIFO: expulsa la pagina que lleva mas tiempo cargada en memoria.
- LRU: expulsa la pagina que no se ha usado desde hace mas tiempo.

La interfaz permite configurar cuantos marcos fisicos hay disponibles
y muestra cada reemplazo animado paso a paso.

### Acceso concurrente a archivos

Simula varios procesos intentando leer y escribir archivos al mismo tiempo.
Se usa el patron Lectores-Escritores con semaforos:

- Multiples procesos pueden leer el mismo archivo al mismo tiempo.
- Solo un proceso puede escribir, y mientras escribe bloquea a los demas.
- Si un proceso es bloqueado, espera y reintenta automaticamente.

Se registran todos los accesos, bloqueos y resoluciones.

---

## Clases principales

| Clase | Descripcion |
|---|---|
| Process | Modelo de proceso con todos sus atributos |
| MemoryFrame | Marco de pagina en memoria fisica |
| SharedFile | Archivo compartido con control de acceso por semaforos |
| SchedulerResult | Resultado de una simulacion de planificacion |
| RoundRobinScheduler | Algoritmo Round Robin |
| SJFScheduler | Algoritmo Shortest Job First |
| PriorityScheduler | Algoritmo por prioridad con aging |
| MemoryManager | Gestion de memoria con FIFO y LRU |
| FileAccessManager | Acceso concurrente a archivos |
| SimulatorFacade | Punto unico de acceso a todos los servicios |
| MainController | Coordinacion de navegacion entre vistas |

---

## Decisiones de diseno

El simulador usa una arquitectura en capas (Model - Service - Controller - View).
Las vistas nunca acceden directamente a los servicios; todo pasa por SimulatorFacade.
Esto hace que agregar un nuevo algoritmo o modulo no requiera modificar la interfaz grafica.

Los algoritmos trabajan sobre copias de los procesos para no alterar el estado
original, lo que permite simular el mismo conjunto de procesos con distintos
algoritmos sin tener que volver a crearlos.
