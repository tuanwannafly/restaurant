package com.restaurant.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

/**
 * ImageLoader — Phase 6A
 *
 * Utility class để load ảnh món ăn bất đồng bộ (không block EDT).
 * Hỗ trợ:
 *   - URL http/https
 *   - Đường dẫn file local
 *   - In-memory cache (ConcurrentHashMap)
 *   - Placeholder tự động khi URL null/blank hoặc load thất bại
 */
public class ImageLoader {

    // ─── Cache ────────────────────────────────────────────────────────────────
    private static final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

    // ─── Dimensions ───────────────────────────────────────────────────────────
    public static final int IMG_W = 120;
    public static final int IMG_H = 120;

    // ─── Placeholder (lazy-init singleton) ───────────────────────────────────
    private static final ImageIcon PLACEHOLDER = createPlaceholder();

    // ─── Private constructor — utility class ──────────────────────────────────
    private ImageLoader() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Load ảnh từ urlOrPath vào target JLabel bất đồng bộ.
     * Hiển thị PLACEHOLDER ngay lập tức, sau đó swap sang ảnh thật khi load xong.
     *
     * @param urlOrPath URL http/https hoặc đường dẫn file tuyệt đối / tương đối
     * @param target    JLabel sẽ nhận ImageIcon khi load hoàn tất
     */
    public static void loadAsync(String urlOrPath, JLabel target) {
        // null / blank → giữ placeholder, không cần worker
        if (urlOrPath == null || urlOrPath.isBlank()) {
            target.setIcon(PLACEHOLDER);
            return;
        }

        // Cache hit → set ngay, không tạo worker
        ImageIcon cached = cache.get(urlOrPath);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        // Hiện placeholder trong lúc chờ
        target.setIcon(PLACEHOLDER);

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage raw;
                if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                    raw = ImageIO.read(new URL(urlOrPath));
                } else {
                    raw = ImageIO.read(new File(urlOrPath));
                }

                if (raw == null) {
                    throw new IllegalStateException("ImageIO.read() trả về null");
                }

                // Scale giữ tỉ lệ, vừa khung IMG_W × IMG_H
                Image scaled = raw.getScaledInstance(IMG_W, IMG_H, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    cache.put(urlOrPath, icon);
                    target.setIcon(icon);
                    // Yêu cầu repaint container cha nếu cần
                    Container parent = target.getParent();
                    if (parent != null) {
                        parent.revalidate();
                        parent.repaint();
                    }
                } catch (Exception e) {
                    // Load thất bại → fallback về placeholder, KHÔNG log stack trace
                    target.setIcon(PLACEHOLDER);
                    System.err.println("[ImageLoader] Không load được ảnh: "
                            + urlOrPath + " — " + e.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Xóa một entry khỏi cache.
     * Gọi sau khi admin cập nhật imageUrl của một món.
     *
     * @param urlOrPath key cần xóa
     */
    public static void invalidate(String urlOrPath) {
        if (urlOrPath != null) {
            cache.remove(urlOrPath);
        }
    }

    /**
     * Xóa toàn bộ cache — dùng khi reload menu hoàn toàn.
     */
    public static void clearCache() {
        cache.clear();
    }

    // ─── Placeholder builder ──────────────────────────────────────────────────

    /**
     * Tạo placeholder 120×120 màu xám nhạt với emoji 🍽 căn giữa.
     * Được tạo một lần duy nhất khi class load.
     */
    private static ImageIcon createPlaceholder() {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền xám nhạt
        g2.setColor(new Color(0xF3F4F6));
        g2.fillRoundRect(0, 0, IMG_W, IMG_H, 12, 12);

        // Emoji 🍽
        String emoji = "🍽";
        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 36);
        g2.setFont(emojiFont);
        g2.setColor(new Color(0xD1D5DB));
        FontMetrics fm = g2.getFontMetrics();
        int ex = (IMG_W - fm.stringWidth(emoji)) / 2;
        int ey = (IMG_H + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(emoji, ex, ey);

        g2.dispose();
        return new ImageIcon(img);
    }
}