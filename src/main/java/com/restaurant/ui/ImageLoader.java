package com.restaurant.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * ImageLoader — Phase 6A (JavaFX port)
 *
 * <p>Utility class that loads images asynchronously using JavaFX {@link Task},
 * so the Application Thread (AT) is never blocked.
 *
 * <p>Features:
 * <ul>
 *   <li>Loads from HTTP/HTTPS URLs <em>or</em> local file paths.</li>
 *   <li>In-memory LRU-like cache backed by a {@link ConcurrentHashMap}.</li>
 *   <li>Shows a grey placeholder immediately; swaps to the real image when ready.</li>
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
    /** urlOrPath → Image (JavaFX Image, created on AT or loaded from background). */
    private static final Map<String, Image> cache = new ConcurrentHashMap<>();

    // ── Placeholder ───────────────────────────────────────────────────────────
    /** Lazy singleton; created once on first use. */
    private static volatile Image placeholder;

    /**
     * Background thread pool — daemon threads so they don't prevent JVM exit.
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(
        3, r -> { Thread t = new Thread(r, "img-loader"); t.setDaemon(true); return t; });

    // ── Private constructor ───────────────────────────────────────────────────
    private ImageLoader() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load an image from {@code urlOrPath} into {@code target} asynchronously.
     *
     * <p>The target's image is set to the placeholder immediately, then swapped
     * to the loaded image on the Application Thread when the background task
     * completes.
     *
     * @param urlOrPath HTTP/HTTPS URL or local file path (absolute or relative)
     * @param target    {@link ImageView} to receive the loaded image
     */
    public static void loadAsync(String urlOrPath, ImageView target) {
        // null / blank → show placeholder
        if (urlOrPath == null || urlOrPath.isBlank()) {
            target.setImage(getPlaceholder());
            return;
        }

        // Cache hit → show immediately
        Image cached = cache.get(urlOrPath);
        if (cached != null) {
            target.setImage(cached);
            return;
        }

        // Show placeholder while loading
        target.setImage(getPlaceholder());

        // Build and submit background task
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
     * Variant that calls a {@link Consumer} with the loaded {@link Image}
     * instead of setting it on an {@link ImageView} directly.
     * Useful when you need to apply the image to multiple targets.
     *
     * @param urlOrPath  HTTP/HTTPS URL or local file path
     * @param onLoaded   callback invoked on the Application Thread with the result
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

    /**
     * Remove a single entry from the cache.
     * Call this after updating a menu item's {@code imageUrl}.
     *
     * @param urlOrPath cache key to remove
     */
    public static void invalidate(String urlOrPath) {
        if (urlOrPath != null) cache.remove(urlOrPath);
    }

    /**
     * Flush the entire image cache.
     * Call when performing a full menu reload (e.g., restaurant switch).
     */
    public static void clearCache() {
        cache.clear();
    }

    // ── Background task builder ───────────────────────────────────────────────

    /**
     * Builds a {@link Task} that reads the image from a URL or local file,
     * then scales it to {@link #IMG_W} × {@link #IMG_H} using AWT for quality,
     * and returns a JavaFX {@link Image}.
     */
    private static Task<Image> buildLoadTask(String urlOrPath) {
        return new Task<>() {
            @Override
            protected Image call() throws Exception {
                // ① Read raw BufferedImage via AWT (supports more formats)
                BufferedImage raw;
                if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                    try (InputStream is = new URL(urlOrPath).openStream()) {
                        raw = ImageIO.read(is);
                    }
                } else {
                    raw = ImageIO.read(new File(urlOrPath));
                }

                if (raw == null) {
                    throw new IllegalStateException("ImageIO.read() trả về null cho: " + urlOrPath);
                }

                // ② Scale using AWT (SCALE_SMOOTH) — better quality than JavaFX default
                java.awt.Image scaled = raw.getScaledInstance(IMG_W, IMG_H, java.awt.Image.SCALE_SMOOTH);
                java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(IMG_W, IMG_H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = out.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(scaled, 0, 0, null);
                g2.dispose();

                // ③ Convert AWT BufferedImage → JavaFX WritableImage
                return awtToFX(out);
            }
        };
    }

    // ── AWT → JavaFX image conversion ────────────────────────────────────────

    /**
     * Converts an AWT {@link BufferedImage} to a JavaFX {@link Image}
     * by writing it to a byte array and reading it back with JavaFX.
     */
    private static Image awtToFX(BufferedImage awtImage) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(awtImage, "png", baos);
        try (java.io.ByteArrayInputStream bais =
                 new java.io.ByteArrayInputStream(baos.toByteArray())) {
            return new Image(bais);
        }
    }

    // ── Placeholder ───────────────────────────────────────────────────────────

    /**
     * Returns a 120×120 light-grey placeholder image with a centred 🍽 emoji.
     * Created lazily (once) using AWT, then cached in the static field.
     */
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

    private static Image buildPlaceholder() {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Light-grey rounded background
        g2.setColor(new java.awt.Color(0xF3F4F6));
        g2.fillRoundRect(0, 0, IMG_W, IMG_H, 16, 16);

        // Plate emoji centred
        String emoji = "🍽";
        java.awt.Font font = new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN, 36);
        g2.setFont(font);
        g2.setColor(new java.awt.Color(0xD1D5DB));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int ex = (IMG_W - fm.stringWidth(emoji)) / 2;
        int ey = (IMG_H + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(emoji, ex, ey);

        g2.dispose();

        try {
            return awtToFX(img);
        } catch (Exception e) {
            // Absolute fallback — transparent 1×1 image
            return new Image(new java.io.ByteArrayInputStream(new byte[0]));
        }
    }
}