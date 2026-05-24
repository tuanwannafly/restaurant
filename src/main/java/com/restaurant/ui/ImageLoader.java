package com.restaurant.ui;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ImageLoader — pure JavaFX implementation (no java.awt / javax.imageio).
 *
 * <p>Loads images asynchronously so the FX Application Thread is never blocked.
 *
 * <p>Features:
 * <ul>
 *   <li>Loads from HTTP/HTTPS URLs <em>or</em> local file paths.</li>
 *   <li>In-memory cache backed by a {@link ConcurrentHashMap}.</li>
 *   <li>Shows a grey placeholder immediately; swaps to real image when ready.</li>
 *   <li>Falls back to the placeholder silently on any load failure.</li>
 *   <li>{@link #invalidate(String)} and {@link #clearCache()} for cache management.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 *   ImageLoader.loadAsync(item.getImageUrl(), myImageView);
 * }</pre>
 */
public final class ImageLoader {

    // ── Dimensions ────────────────────────────────────────────────────────────
    public static final int IMG_W = 120;
    public static final int IMG_H = 120;

    // ── Cache ─────────────────────────────────────────────────────────────────
    private static final Map<String, Image> cache = new ConcurrentHashMap<>();

    // ── Placeholder ───────────────────────────────────────────────────────────
    private static volatile Image placeholder;

    // ── Thread pool ───────────────────────────────────────────────────────────
    private static final ExecutorService executor = Executors.newFixedThreadPool(
        3, r -> { Thread t = new Thread(r, "img-loader"); t.setDaemon(true); return t; });

    private ImageLoader() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load an image from {@code urlOrPath} into {@code target} asynchronously.
     * The target is set to the placeholder immediately, then swapped to the
     * loaded image on the FX Application Thread when ready.
     *
     * @param urlOrPath HTTP/HTTPS URL or local file path
     * @param target    {@link ImageView} to receive the image
     */
    public static void loadAsync(String urlOrPath, ImageView target) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            target.setImage(getPlaceholder());
            return;
        }

        Image cached = cache.get(urlOrPath);
        if (cached != null) {
            target.setImage(cached);
            return;
        }

        target.setImage(getPlaceholder());

        Task<Image> task = buildLoadTask(urlOrPath);
        task.setOnSucceeded(e -> {
            Image img = task.getValue();
            cache.put(urlOrPath, img);
            target.setImage(img);
        });
        task.setOnFailed(e -> {
            System.err.println("[ImageLoader] Không load được ảnh: "
                + urlOrPath + " — " + task.getException().getMessage());
            target.setImage(getPlaceholder());
        });

        executor.submit(task);
    }

    /**
     * Variant that delivers the loaded {@link Image} to a {@link Consumer}
     * instead of setting it directly on an {@link ImageView}.
     *
     * @param urlOrPath HTTP/HTTPS URL or local file path
     * @param onLoaded  callback invoked on the FX Application Thread
     */
    public static void loadAsync(String urlOrPath, Consumer<Image> onLoaded) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            onLoaded.accept(getPlaceholder());
            return;
        }

        Image cached = cache.get(urlOrPath);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }

        Task<Image> task = buildLoadTask(urlOrPath);
        task.setOnSucceeded(e -> {
            Image img = task.getValue();
            cache.put(urlOrPath, img);
            onLoaded.accept(img);
        });
        task.setOnFailed(e -> {
            System.err.println("[ImageLoader] Không load được ảnh: "
                + urlOrPath + " — " + task.getException().getMessage());
            onLoaded.accept(getPlaceholder());
        });

        executor.submit(task);
    }

    /** Remove a single entry from the cache (e.g., after updating an image URL). */
    public static void invalidate(String urlOrPath) {
        if (urlOrPath != null) cache.remove(urlOrPath);
    }

    /** Flush the entire image cache (e.g., on restaurant switch). */
    public static void clearCache() {
        cache.clear();
    }

    // ── Background task ───────────────────────────────────────────────────────

    /**
     * Builds a {@link Task} that loads the image using JavaFX {@link Image}.
     * Converts local file paths to {@code file:} URIs automatically.
     */
    private static Task<Image> buildLoadTask(String urlOrPath) {
        return new Task<>() {
            @Override
            protected Image call() throws Exception {
                String uri = toUri(urlOrPath);
                // JavaFX Image loads synchronously here (on background thread).
                // preserveRatio=true, smooth=true, backgroundLoading=false because
                // we are already on a background thread — no need to double-defer.
                Image img = new Image(uri, IMG_W, IMG_H, true, true, false);
                if (img.isError()) {
                    throw new IllegalStateException(
                        "JavaFX Image error: " + img.getException().getMessage());
                }
                return img;
            }
        };
    }

    /** Converts a local path to a {@code file:} URI; leaves HTTP(S) URLs unchanged. */
    private static String toUri(String urlOrPath) {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")
                || urlOrPath.startsWith("file:")) {
            return urlOrPath;
        }
        return new File(urlOrPath).toURI().toString();
    }

    // ── Placeholder ───────────────────────────────────────────────────────────

    private static Image getPlaceholder() {
        if (placeholder == null) {
            synchronized (ImageLoader.class) {
                if (placeholder == null) {
                    placeholder = buildPlaceholder();
                }
            }
        }
        return placeholder;
    }

    /**
     * Builds a simple light-grey {@link WritableImage} as a fallback placeholder.
     * Pure JavaFX — no AWT required.
     */
    private static Image buildPlaceholder() {
        WritableImage img = new WritableImage(IMG_W, IMG_H);
        PixelWriter pw = img.getPixelWriter();

        // Fill with light-grey (#F3F4F6)
        int grey = 0xFFF3F4F6;
        for (int y = 0; y < IMG_H; y++) {
            for (int x = 0; x < IMG_W; x++) {
                pw.setArgb(x, y, grey);
            }
        }
        return img;
    }
}
