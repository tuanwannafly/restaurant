package com.restaurant.ui.fx.util;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * FxUtils — JavaFX edition of the old Swing utility helpers.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>{@link #loadFxml(String, Object)} – type-safe FXML loading with
 *       controller injection.</li>
 *   <li>{@link #showToast(Window, String, ToastType, int)} – non-blocking
 *       bottom-centre toast notifications (replaces
 *       {@code JOptionPane.showMessageDialog} for transient feedback).</li>
 *   <li>{@link #runAsync(Callable, Consumer, Consumer)} – background work on
 *       a daemon thread with success/failure callbacks marshalled back to
 *       the JavaFX Application Thread.</li>
 *   <li>{@link #loadCss(Scene)} – applies the global {@code app.css} to any
 *       new Scene.</li>
 *   <li>{@link #openModal(String, String, Object, Window)} – opens an FXML
 *       as a modal Stage.</li>
 * </ul>
 *
 * <h3>Thread contract</h3>
 * All methods except {@code runAsync} must be called on the FX Application
 * Thread. {@code runAsync} may be called from any thread.
 */
public final class FxUtils {

    // ── CSS resource path (classpath-relative) ───────────────────────────────
    private static final String CSS_PATH = "/css/app.css";

    // ── FXML root directory (classpath-relative) ─────────────────────────────
    private static final String FXML_ROOT = "/fxml/";

    private FxUtils() {}

    // =========================================================================
    // loadFxml
    // =========================================================================

    /**
     * Loads an FXML file from the classpath and returns the root node.
     *
     * <p>The controller class is set on the {@link FXMLLoader} <b>before</b>
     * loading, so {@code fx:controller} attributes in the FXML file are
     * optional (and overridden when {@code controller} is non-null).</p>
     *
     * @param <T>        expected return type (e.g. {@code VBox}, {@code AnchorPane})
     * @param fxmlName   file name relative to {@code /fxml/} (e.g.
     *                   {@code "LoginView.fxml"})
     * @param controller controller instance to inject, or {@code null} to use
     *                   the one declared in the FXML
     * @return the loaded root node
     * @throws RuntimeException wrapping {@link IOException} if loading fails
     */
    @SuppressWarnings("unchecked")
    public static <T extends Parent> T loadFxml(String fxmlName, Object controller) {
        assertFxThread("loadFxml");

        String path = FXML_ROOT + fxmlName;
        URL url = FxUtils.class.getResource(path);

        Objects.requireNonNull(url,
                "FXML resource not found on classpath: " + path
                + " — check that the file exists under src/main/resources/fxml/");

        FXMLLoader loader = new FXMLLoader(url);
        if (controller != null) {
            loader.setController(controller);
        }

        try {
            return (T) loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + fxmlName, e);
        }
    }

    /**
     * Convenience overload: loads FXML without an explicit controller
     * (uses {@code fx:controller} declared inside the file).
     */
    public static <T extends Parent> T loadFxml(String fxmlName) {
        return loadFxml(fxmlName, null);
    }

    /**
     * Returns the {@link FXMLLoader} so callers can retrieve the controller
     * after loading, avoiding a second load call.
     *
     * <p>Usage:
     * <pre>{@code
     *   FXMLLoader loader = FxUtils.createLoader("KitchenView.fxml", null);
     *   VBox root = loader.load();
     *   KitchenController ctrl = loader.getController();
     * }</pre>
     */
    public static FXMLLoader createLoader(String fxmlName, Object controller) {
        String path = FXML_ROOT + fxmlName;
        URL url = FxUtils.class.getResource(path);
        Objects.requireNonNull(url, "FXML not found: " + path);

        FXMLLoader loader = new FXMLLoader(url);
        if (controller != null) loader.setController(controller);
        return loader;
    }

    // =========================================================================
    // showToast
    // =========================================================================

    /** Severity of a toast notification — controls the CSS class applied. */
    public enum ToastType { SUCCESS, ERROR, INFO, WARNING }

    /**
     * Shows a self-dismissing toast notification anchored to the bottom-centre
     * of {@code owner}.
     *
     * <p>The toast:
     * <ol>
     *   <li>Fades in over 200 ms.</li>
     *   <li>Stays visible for {@code durationMs} milliseconds.</li>
     *   <li>Fades out over 300 ms then auto-closes.</li>
     * </ol>
     *
     * @param owner      the {@link Window} above which the toast is displayed
     * @param message    plain-text message (keep ≤ 80 chars for readability)
     * @param type       controls background colour via CSS
     * @param durationMs how long (ms) the toast is fully visible
     */
    public static void showToast(Window owner, String message,
                                 ToastType type, int durationMs) {
        assertFxThread("showToast");

        // ── Build toast label ─────────────────────────────────────────────────
        Label label = new Label(message);
        label.getStyleClass().addAll("toast", toastStyleClass(type));
        label.setWrapText(true);
        label.setMaxWidth(420);

        // ── Wrap in a transparent overlay stage ───────────────────────────────
        StackPane root = new StackPane(label);
        root.setAlignment(Pos.BOTTOM_CENTER);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new javafx.geometry.Insets(0, 0, 32, 0));

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        loadCss(scene);

        Stage toastStage = new Stage(StageStyle.TRANSPARENT);
        toastStage.initOwner(owner);
        toastStage.initModality(Modality.NONE);
        toastStage.setScene(scene);
        toastStage.setAlwaysOnTop(true);

        // Centre below owner
        toastStage.setWidth(500);
        toastStage.setHeight(80);
        if (owner != null) {
            toastStage.setX(owner.getX() + (owner.getWidth()  - 500) / 2);
            toastStage.setY(owner.getY() +  owner.getHeight() - 100);
        }

        toastStage.show();

        // ── Animation sequence ────────────────────────────────────────────────
        FadeTransition fadeIn  = new FadeTransition(Duration.millis(200), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> toastStage.close());

        Timeline hold = new Timeline(
                new KeyFrame(Duration.millis(durationMs), e -> fadeOut.play()));

        fadeIn.setOnFinished(e -> hold.play());
        fadeIn.play();
    }

    /** Convenience: shows a toast for 3 seconds. */
    public static void showToast(Window owner, String message, ToastType type) {
        showToast(owner, message, type, 3000);
    }

    /** Convenience: shows a SUCCESS toast for 3 seconds. */
    public static void showToast(Window owner, String message) {
        showToast(owner, message, ToastType.INFO, 3000);
    }

    private static String toastStyleClass(ToastType type) {
        return switch (type) {
            case SUCCESS -> "toast-success";
            case ERROR   -> "toast-error";
            case WARNING -> "toast-warning";
            default      -> "toast-info";
        };
    }

    // =========================================================================
    // runAsync
    // =========================================================================

    /**
     * Executes {@code work} on a background daemon thread.
     *
     * <p>On completion, {@code onSuccess} (or {@code onError}) is invoked on
     * the FX Application Thread via {@link Platform#runLater}. This keeps the
     * UI responsive during DB queries, network calls, etc.
     *
     * <p>Replaces the old pattern of {@code SwingWorker} / anonymous
     * {@code Thread} used in the Swing codebase.
     *
     * <h4>Usage example</h4>
     * <pre>{@code
     * FxUtils.runAsync(
     *     () -> orderDAO.findAll(restaurantId),   // background
     *     orders -> tableView.setItems(           // FX thread
     *         FXCollections.observableArrayList(orders)),
     *     err -> FxUtils.showToast(
     *         getScene().getWindow(),
     *         "Load failed: " + err.getMessage(),
     *         ToastType.ERROR)
     * );
     * }</pre>
     *
     * @param <T>       result type
     * @param work      callable executed on background thread
     * @param onSuccess called on FX thread with the result
     * @param onError   called on FX thread if {@code work} throws; may be null
     */
    public static <T> void runAsync(Callable<T> work,
                                    Consumer<T> onSuccess,
                                    Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(e -> {
            if (onSuccess != null) onSuccess.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.printf("[FxUtils] runAsync task failed: %s – %s%n",
                    ex.getClass().getSimpleName(), ex.getMessage());
            if (onError != null) {
                onError.accept(ex);
            }
        });

        Thread thread = new Thread(task, "fx-async-worker");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Convenience overload — swallows errors (logs to stderr only).
     */
    public static <T> void runAsync(Callable<T> work, Consumer<T> onSuccess) {
        runAsync(work, onSuccess, null);
    }

    /**
     * Fire-and-forget: run a task with no return value on a background thread.
     */
    public static void runAsync(Runnable work) {
        runAsync(() -> { work.run(); return null; }, ignored -> {});
    }

    // =========================================================================
    // CSS helpers
    // =========================================================================

    /**
     * Applies {@code app.css} to the given scene.
     * Idempotent: skips if the stylesheet is already present.
     *
     * @param scene target scene; must not be null
     */
    public static void loadCss(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");

        URL cssUrl = FxUtils.class.getResource(CSS_PATH);
        if (cssUrl == null) {
            System.err.println("[FxUtils] CSS not found at classpath:" + CSS_PATH);
            return;
        }

        String externalForm = cssUrl.toExternalForm();
        if (!scene.getStylesheets().contains(externalForm)) {
            scene.getStylesheets().add(externalForm);
        }
    }

    // =========================================================================
    // Modal window helper
    // =========================================================================

    /**
     * Opens an FXML view as a modal dialog window.
     *
     * @param fxmlName   e.g. {@code "EditTableDialog.fxml"}
     * @param title      window title bar text
     * @param controller controller to inject (may be null)
     * @param owner      parent window for modality; may be null
     * @return the opened {@link Stage} (already visible)
     */
    public static Stage openModal(String fxmlName, String title,
                                  Object controller, Window owner) {
        assertFxThread("openModal");

        Parent root = loadFxml(fxmlName, controller);

        Scene scene = new Scene(root);
        loadCss(scene);

        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setResizable(false);
        stage.show();

        return stage;
    }

    /**
     * Same as {@link #openModal} but blocks until the dialog is closed
     * ({@code showAndWait} semantics).
     */
    public static Stage openModalAndWait(String fxmlName, String title,
                                         Object controller, Window owner) {
        assertFxThread("openModalAndWait");

        Parent root = loadFxml(fxmlName, controller);

        Scene scene = new Scene(root);
        loadCss(scene);

        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setResizable(false);
        stage.showAndWait();

        return stage;
    }

    // =========================================================================
    // Misc
    // =========================================================================

    /**
     * Ensures a {@link PauseTransition} runs on the FX thread after a delay.
     * Useful for debouncing search-as-you-type inputs.
     *
     * @param delayMs  delay in milliseconds
     * @param action   action to run after delay
     * @return a started {@link PauseTransition} (call {@code .stop()} to cancel)
     */
    public static PauseTransition debounce(int delayMs, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
        pause.setOnFinished(e -> action.run());
        pause.play();
        return pause;
    }

    /**
     * Soft assertion: logs a warning if called from outside the FX thread.
     * Does NOT throw — avoids crashing production code while still flagging
     * threading bugs during development.
     */
    private static void assertFxThread(String methodName) {
        if (!Platform.isFxApplicationThread()) {
            System.err.printf(
                "[FxUtils] WARN: %s() called from non-FX thread (%s). " +
                "Use Platform.runLater() or FxUtils.runAsync().%n",
                methodName, Thread.currentThread().getName());
        }
    }
}