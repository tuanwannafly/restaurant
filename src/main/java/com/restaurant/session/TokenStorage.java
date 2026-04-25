package com.restaurant.session;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Lưu trữ và đọc refresh token trên đĩa cục bộ với mã hoá <b>AES-256-CBC</b>.
 *
 * <h3>Vị trí file</h3>
 * {@code ~/.restaurant_app/session.dat} — thư mục ẩn trong home của OS user hiện tại.
 *
 * <h3>Bảo mật</h3>
 * <ul>
 *   <li>Key 256-bit được derive từ <b>MAC address</b> của network interface đầu tiên
 *       thông qua SHA-256, đảm bảo key khác nhau trên mỗi máy.</li>
 *   <li>IV ngẫu nhiên 16 byte được prepend vào ciphertext trước khi Base64 encode
 *       → mỗi lần ghi file có ciphertext khác nhau dù token giống nhau.</li>
 *   <li>File này chỉ có ý nghĩa trên đúng máy đã tạo ra nó.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> Singleton initialization-on-demand; IO thực hiện
 * bên ngoài EDT qua SwingWorker — tuy nhiên các method đều thread-safe.
 */
public final class TokenStorage {

    private static final String DIR_NAME  = ".restaurant_app";
    private static final String FILE_NAME = "session.dat";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private TokenStorage() {}

    private static final class Holder {
        static final TokenStorage INSTANCE = new TokenStorage();
    }

    public static TokenStorage getInstance() {
        return Holder.INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Mã hoá {@code token} bằng AES-256-CBC với key từ machine-id, ghi vào
     * {@code ~/.restaurant_app/session.dat}.
     * <p>
     * Gọi method này từ background thread (không block EDT).
     *
     * @param token refresh token cần lưu; null/blank → clearSavedToken()
     */
    public void saveRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            clearSavedToken();
            return;
        }

        try {
            byte[]    iv         = generateIv();
            SecretKey key        = deriveKey();
            Cipher    cipher     = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // Format: Base64( IV_16bytes || ciphertext )
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv,        0, combined, 0,          iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length,  encrypted.length);

            String encoded = Base64.getEncoder().encodeToString(combined);

            Path dir  = getStorageDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE_NAME), encoded, StandardCharsets.UTF_8);

        } catch (Exception e) {
            System.err.println("[TokenStorage] saveRefreshToken lỗi: " + e.getMessage());
        }
    }

    /**
     * Đọc file {@code session.dat}, giải mã và trả về refresh token.
     * <p>
     * Trả về {@code Optional.empty()} nếu:
     * <ul>
     *   <li>File không tồn tại (chưa từng lưu hoặc đã clearSavedToken)</li>
     *   <li>Giải mã thất bại (file bị sửa tay, hoặc chạy trên máy khác)</li>
     * </ul>
     *
     * @return Optional chứa token nếu đọc/giải mã thành công; ngược lại empty
     */
    public Optional<String> loadRefreshToken() {
        Path file = getStorageDir().resolve(FILE_NAME);
        if (!Files.exists(file)) return Optional.empty();

        try {
            String encoded  = Files.readString(file, StandardCharsets.UTF_8).trim();
            byte[] combined = Base64.getDecoder().decode(encoded);

            if (combined.length <= 16) return Optional.empty(); // malformed

            // Tách IV và ciphertext
            byte[] iv         = new byte[16];
            byte[] ciphertext = new byte[combined.length - 16];
            System.arraycopy(combined, 0,  iv,         0, 16);
            System.arraycopy(combined, 16, ciphertext, 0, ciphertext.length);

            SecretKey key    = deriveKey();
            Cipher    cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] decrypted = cipher.doFinal(ciphertext);
            String token     = new String(decrypted, StandardCharsets.UTF_8);

            return token.isBlank() ? Optional.empty() : Optional.of(token);

        } catch (Exception e) {
            System.err.println("[TokenStorage] loadRefreshToken lỗi (file có thể bị hỏng): "
                    + e.getMessage());
            clearSavedToken(); // Xoá file hỏng để tránh retry vô ích
            return Optional.empty();
        }
    }

    /**
     * Xoá file token đã lưu trên disk (gọi khi logout hoặc khi token bị revoke).
     * Idempotent: không throw nếu file không tồn tại.
     */
    public void clearSavedToken() {
        try {
            Files.deleteIfExists(getStorageDir().resolve(FILE_NAME));
        } catch (IOException e) {
            System.err.println("[TokenStorage] clearSavedToken lỗi: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derive AES-256 key từ MAC address của NetworkInterface đầu tiên.
     * SHA-256(macHex) → 32 bytes → AES key.
     * <p>
     * Fallback: nếu không lấy được MAC → dùng hostname.
     */
    private static SecretKey deriveKey() throws Exception {
        String keyMaterial = getMachineId();
        MessageDigest sha  = MessageDigest.getInstance("SHA-256");
        byte[]        hash = sha.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES"); // 256-bit key
    }

    /**
     * Lấy MAC address của interface đầu tiên không phải loopback.
     * Fallback về hostname nếu không có interface phù hợp.
     */
    private static String getMachineId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface ni : Collections.list(interfaces)) {
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        return HexFormat.of().formatHex(mac);
                    }
                }
            }
        } catch (Exception ignored) { /* fall through to hostname */ }

        // Fallback: hostname
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "RESTAURANT_APP_FALLBACK_MACHINE_KEY_V1";
        }
    }

    /** Sinh IV ngẫu nhiên 16 byte cho AES/CBC. */
    private static byte[] generateIv() {
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }

    /** Trả về đường dẫn thư mục lưu trữ: {@code ~/.restaurant_app/}. */
    private static Path getStorageDir() {
        return Paths.get(System.getProperty("user.home"), DIR_NAME);
    }
}
