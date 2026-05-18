#!/bin/bash
set -e

# ── Khởi động Xvfb (màn hình ảo) ─────────────────────────────────────
Xvfb :99 -screen 0 1280x800x24 &
export DISPLAY=:99

# FIX: Chờ Xvfb thực sự sẵn sàng thay vì sleep cố định
# (sleep 1 không đủ trong môi trường Kubernetes chậm hơn)
for i in $(seq 1 10); do
    xdpyinfo -display :99 >/dev/null 2>&1 && break
    echo "Waiting for Xvfb... attempt $i"
    sleep 1
done

# ── Khởi động Fluxbox (window manager) ───────────────────────────────
fluxbox &

# ── Khởi động VNC server (nếu được bật) ──────────────────────────────
if [ "${VNC_ENABLED}" = "true" ]; then
    if [ -n "${VNC_PASSWORD}" ]; then
        x11vnc -display :99 -rfbport "${VNC_PORT}" -passwd "${VNC_PASSWORD}" -forever -bg -quiet
    else
        x11vnc -display :99 -rfbport "${VNC_PORT}" -nopw -forever -bg -quiet
    fi
fi

# ── Chạy ứng dụng JavaFX bằng MODULE PATH ────────────────────────────
# QUAN TRỌNG: Project dùng JPMS (module-info.java) nên KHÔNG dùng -jar.
#   -p libs:app.jar   → module path = thư mục libs/ + thin JAR ứng dụng
#   -m com.restaurant/com.restaurant.Main  → module/main-class
#
# FIX: Thêm -Djdk.gtk.version=2 để tránh lỗi GtkApplication Internal Error
#      trong môi trường container (GTK3 không ổn định với Xvfb headless).
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