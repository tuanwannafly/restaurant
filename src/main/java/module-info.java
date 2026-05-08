/**
 * SmartRestaurant – JavaFX edition module descriptor.
 *
 * Design rules:
 *  • "requires" every javafx module actually used (controls, fxml, media).
 *  • "opens … to javafx.fxml" for every package that contains @FXML controllers
 *    so the FXMLLoader can reflectively inject fields.
 *  • "opens … to javafx.base" for model/entity packages whose JavaBean
 *    properties are bound to TableColumn / ObservableList.
 *  • DAO / session packages are NOT opened – they are pure Java with no
 *    framework reflection requirement.
 *  • Logging via SLF4J → Logback (transitive from logback-classic).
 *
 * Phase WS: Thêm org.java_websocket cho WebSocket push infrastructure.
 */
module com.restaurant {

    // ── JavaFX ───────────────────────────────────────────────────────────────

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.web;          // Charts (BarChart, LineChart) via javafx.scene.chart

    // ── Database ─────────────────────────────────────────────────────────────

    requires java.sql;            // java.sql.DriverManager, Connection, etc.
    requires org.xerial.sqlitejdbc;
    requires com.zaxxer.hikari;

    // ── Security ─────────────────────────────────────────────────────────────

    requires jbcrypt;

    // ── Serialisation ────────────────────────────────────────────────────────

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // ── Logging ──────────────────────────────────────────────────────────────

    requires org.slf4j;

    // ── WebSocket (Phase WS) ─────────────────────────────────────────────────
    // Artifact: org.java-websocket:Java-WebSocket:1.5.4
    // Module name: org.java_websocket  (dấu gạch dưới, không phải dấu gạch ngang)

    requires org.java_websocket;

    // ── JDK utilities used by business logic ─────────────────────────────────

    requires java.desktop;        // java.awt.* still used in non-UI utilities
    requires java.prefs;          // TokenStorage (Preferences API)

    // =========================================================================
    // EXPORTS – public API surface
    // =========================================================================

    // Core application entry point
    exports com.restaurant;

    // Business-logic packages consumed across modules / tests
    exports com.restaurant.dao;
    exports com.restaurant.data;
    exports com.restaurant.model;
    exports com.restaurant.session;
    exports com.restaurant.ui.fx.util;

    // WebSocket infrastructure (Phase WS)
    exports com.restaurant.websocket;

    // JavaFX UI packages
    exports com.restaurant.ui.fx.controller;

    // =========================================================================
    // OPENS – reflective access required by JavaFX / Jackson
    // =========================================================================

    // FXMLLoader needs reflective access to every @FXML-annotated controller.
    // Pattern: "opens <pkg> to javafx.fxml"
    opens com.restaurant.ui.fx.controller  to javafx.fxml;

    // ui.* controllers used directly as fx:controller in FXML files
    opens com.restaurant.ui                to javafx.fxml;
    opens com.restaurant.ui.dialog         to javafx.fxml;
    opens com.restaurant.ui.cell           to javafx.fxml;
    opens com.restaurant.ui.control        to javafx.fxml;

    // JavaFX property binding needs reflective access to model fields.
    // Pattern: "opens <pkg> to javafx.base"
    opens com.restaurant.model             to javafx.base, com.fasterxml.jackson.databind;

    // Jackson deserialises session / token DTOs
    opens com.restaurant.session           to com.fasterxml.jackson.databind;
    opens com.restaurant.data              to com.fasterxml.jackson.databind;

    // Allow the JavaFX application thread to open the root package
    opens com.restaurant                   to javafx.fxml, javafx.graphics;
}