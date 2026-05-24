FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM bellsoft/liberica-openjdk-debian:17

RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc x11-utils xauth fluxbox \
        net-tools \
        libgtk-3-0 libgdk-pixbuf2.0-0 libpango-1.0-0 \
        libatk1.0-0 libcairo2 libcairo-gobject2 \
        fonts-noto fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/smart-restaurant-1.0.0.jar app.jar
COPY --from=build /build/target/libs ./libs

# FIX: Tách thành 2 dòng ENV riêng (dòng cũ dính backtick-n gây lỗi syntax)
# FIX: Bỏ JAVA_TOOL_OPTIONS Monocle — xung đột với Xvfb/VNC
ENV GDK_BACKEND=x11
ENV DB_URL="" DB_USERNAME="" DB_PASSWORD=""
ENV DB_DRIVER="oracle.jdbc.OracleDriver"
ENV DB_POOL_MAX="5" DB_POOL_MIN="1" DB_POOL_TIMEOUT="10000"
ENV MAIL_FROM="" MAIL_USERNAME="" MAIL_PASSWORD=""
ENV MAIL_SMTP_HOST="smtp.gmail.com" MAIL_SMTP_PORT="587"
ENV WS_PORT="8080" INSTANCE_ID=""
ENV VNC_PORT="5900" VNC_ENABLED="true" VNC_PASSWORD=""

# FIX: COPY start.sh thay vì dùng RUN printf để không mất các bản vá
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

EXPOSE 8080 5900
ENTRYPOINT ["/app/start.sh"]