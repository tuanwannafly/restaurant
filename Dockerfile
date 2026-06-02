# ═══════════════════════════════════════════════════════════════════
# SmartRestaurant — Multi-stage Dockerfile
# Stage 1 (builder) : ~900 MB  — maven:3.9-eclipse-temurin-17
# Stage 2 (runtime) : ~250 MB  — eclipse-temurin:17-jre  (giảm ~3-4×)
# ═══════════════════════════════════════════════════════════════════


# ───────────────────────────────────────────────────────────────────
# STAGE 1 — BUILD
# Maven + JDK 17 compile và đóng gói thành fat JAR
# ───────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml TRƯỚC → download deps → tạo layer cache riêng
# Nếu pom.xml không thay đổi → Docker reuse layer này, tiết kiệm 3-5 phút
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code
COPY src/ ./src/

# ── Patch source: 2 vấn đề không tương thích Oracle → Docker ──────

# FIX 1: DBConnection.java — xóa connectionTestQuery("SELECT 1 FROM DUAL")
#   Vì PostgreSQL không có bảng DUAL → HikariCP crash khi khởi tạo pool.
#   HikariCP tự dùng Connection.isValid() nên không cần dòng này.
RUN sed -i '/config\.setConnectionTestQuery/d' \
    src/main/java/com/restaurant/db/DBConnection.java

# FIX 2: KitchenLockService + OrderLockService — Redis "localhost" → env var
#   "localhost" không resolve trong Docker network.
#   Redis chạy ở container tên "redis", không phải localhost.
RUN sed -i \
    's|new redis\.clients\.jedis\.Jedis("localhost", 6379)|new redis.clients.jedis.Jedis(System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "redis", 6379)|g' \
    src/main/java/com/restaurant/db/KitchenLockService.java \
    src/main/java/com/restaurant/db/OrderLockService.java

# Build fat JAR — -DskipTests vì test cần Oracle (sẽ fail trong Docker)
RUN mvn package -DskipTests -q

# Fail fast nếu JAR không được tạo
RUN ls -lh target/smart-restaurant-1.0.0.jar


# ───────────────────────────────────────────────────────────────────
# STAGE 2 — RUNTIME
# Chỉ JRE, không có Maven, không có JDK → image nhỏ hơn ~3-4×
# ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

# Download OpenJFX 17 SDK (chỉ native libs + jars cho Linux x64)
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget unzip \
        xvfb x11vnc \
        libgl1 libglu1-mesa libgtk-3-0 libxtst6 libxxf86vm1 libdbus-1-3 \
    && wget -q https://download2.gluonhq.com/openjfx/17.0.13/openjfx-17.0.13_linux-x64_bin-sdk.zip \
       -O /tmp/javafx.zip \
    && unzip -q /tmp/javafx.zip -d /opt/ \
    && rm /tmp/javafx.zip \
    && apt-get remove -y wget unzip \
    && rm -rf /var/lib/apt/lists/*

ENV JAVAFX_HOME=/opt/javafx-sdk-17.0.13

# Tạo sẵn thư mục X11 cho non-root user dùng được
RUN mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix

RUN groupadd -r appuser && useradd -r -g appuser -u 1001 appuser
WORKDIR /app

# Copy fat JAR từ stage builder
COPY --from=builder /app/target/smart-restaurant-1.0.0.jar app.jar

RUN chown -R appuser:appuser /app
USER appuser

# WebSocket server (RestaurantEventServer)
EXPOSE 8025
# VNC server
EXPOSE 5900

# Startup: Xvfb tạo màn hình ảo → x11vnc chia sẻ qua port 5900 → JavaFX render
CMD ["/bin/sh", "-c", \
    "rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 2>/dev/null; \
     Xvfb :99 -screen 0 1280x800x24 -nolisten tcp & \
     sleep 0.5 && \
     x11vnc -display :99 -nopw -forever -shared -bg -noxdamage && \
     export DISPLAY=:99 && \
     exec java \
       -Dfile.encoding=UTF-8 \
       -Djava.awt.headless=false \
       --module-path ${JAVAFX_HOME}/lib \
       --add-modules javafx.controls,javafx.fxml,javafx.base,javafx.graphics \
       -jar /app/app.jar"]