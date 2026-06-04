module com.coresimulator {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.base;
    opens com.coresimulator to javafx.fxml;
    opens com.coresimulator.model to javafx.base;
    opens com.coresimulator.view to javafx.fxml;
    exports com.coresimulator;
    exports com.coresimulator.model;
    exports com.coresimulator.service;
    exports com.coresimulator.controller;
    exports com.coresimulator.view;
}
