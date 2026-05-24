#!/bin/bash
set -e

# ── Dọn lock file cũ nếu container bị restart đột ngột ───────────────
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99

# ── Khởi động Xvfb ───────────────────────────────────────────────────
# FIX: -ac tắt access control → không cần Xauthority file trong container
Xvfb :99 -screen 0 1280x800x24 -ac &
export DISPLAY=:99

# Chờ Xvfb thực sự sẵn sàng
for i in $(seq 1 15); do
    xdpyinfo -display :99 >/dev/null 2>&1 && break
    echo "Waiting for Xvfb... attempt $i"
    sleep 1
done

# ── Khởi động Fluxbox (window manager) ───────────────────────────────
fluxbox &
sleep 1

# ── Khởi động VNC server (nếu được bật) ──────────────────────────────
# FIX: Bỏ -auth guess vì đã dùng -ac (không có Xauthority)
if [ "${VNC_ENABLED}" = "true" ]; then
    if [ -n "${VNC_PASSWORD}" ]; then
        x11vnc -display :99 -rfbport "${VNC_PORT}" \
               -passwd "${VNC_PASSWORD}" -forever -bg -quiet
    else
        x11vnc -display :99 -rfbport "${VNC_PORT}" \
               -nopw -forever -bg -quiet
    fi
fi

# ── Chạy ứng dụng JavaFX ─────────────────────────────────────────────
# FIX: -Djdk.gtk.version=2 tránh lỗi GtkApplication trong container
exec java \
    -Djdk.gtk.version=2 \
    -p /app/libs:/app/app.jar \
    -m com.restaurant/com.restaurant.Main \
    -Ddb.url="${DB_URL}" \
    -Ddb.username="${DB_USERNAME}" \
    -Ddb.password="${DB_PASSWORD}" \
    -Ddb.driver="${DB_DRIVER}" \
    -Ddb.pool.max="${DB_POOL_MAX}" \
    -Ddb.pool.min="${DB_POOL_MIN}" \
    -Ddb.pool.timeout="${DB_POOL_TIMEOUT}" \
    -Dmail.from="${MAIL_FROM}" \
    -Dmail.username="${MAIL_USERNAME}" \
    -Dmail.password="${MAIL_PASSWORD}" \
    -Dmail.smtp.host="${MAIL_SMTP_HOST}" \
    -Dmail.smtp.port="${MAIL_SMTP_PORT}" \
    -Dws.port="${WS_PORT}" \
    -Dinstance.id="${INSTANCE_ID:-$(hostname)}"