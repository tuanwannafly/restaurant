FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM bellsoft/liberica-openjdk-debian:17

RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc x11-utils fluxbox \
        libgtk-3-0 libgdk-pixbuf2.0-0 libpango-1.0-0 \
        libatk1.0-0 libcairo2 libcairo-gobject2 \
        fonts-noto fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/smart-restaurant-1.0.0.jar app.jar
COPY --from=build /build/target/libs ./libs

ENV GDK_BACKEND=x11`nENV JAVA_TOOL_OPTIONS="-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw"
ENV DB_URL="" DB_USERNAME="" DB_PASSWORD=""
ENV DB_DRIVER="oracle.jdbc.OracleDriver"
ENV DB_POOL_MAX="5" DB_POOL_MIN="1" DB_POOL_TIMEOUT="10000"
ENV MAIL_FROM="" MAIL_USERNAME="" MAIL_PASSWORD=""
ENV MAIL_SMTP_HOST="smtp.gmail.com" MAIL_SMTP_PORT="587"
ENV WS_PORT="8080" INSTANCE_ID=""
ENV VNC_PORT="5900" VNC_ENABLED="true" VNC_PASSWORD=""

RUN printf '%s\n' \
    '#!/bin/bash' \
    'set -e' \
    'Xvfb :99 -screen 0 1280x800x24 &' \
    'export DISPLAY=:99' \
    'for i in $(seq 1 15); do' \
    '    xdpyinfo -display :99 >/dev/null 2>&1 && break' \
    '    echo "Waiting for Xvfb $i"' \
    '    sleep 1' \
    'done' \
    'fluxbox &' \
    'sleep 2' \
    'if [ "${VNC_ENABLED}" = "true" ]; then' \
    '    if [ -n "${VNC_PASSWORD}" ]; then' \
    '        x11vnc -display :99 -rfbport "${VNC_PORT}" -passwd "${VNC_PASSWORD}" -forever -bg -quiet' \
    '    else' \
    '        x11vnc -display :99 -rfbport "${VNC_PORT}" -nopw -forever -bg -quiet' \
    '    fi' \
    'fi' \
    'exec java \' \
    '    -p /app/libs:/app/app.jar \' \
    '    -m com.restaurant/com.restaurant.Main \' \
    '    -Ddb.url="${DB_URL}" \' \
    '    -Ddb.username="${DB_USERNAME}" \' \
    '    -Ddb.password="${DB_PASSWORD}" \' \
    '    -Ddb.driver="${DB_DRIVER}" \' \
    '    -Ddb.pool.max="${DB_POOL_MAX}" \' \
    '    -Ddb.pool.min="${DB_POOL_MIN}" \' \
    '    -Ddb.pool.timeout="${DB_POOL_TIMEOUT}" \' \
    '    -Dmail.from="${MAIL_FROM}" \' \
    '    -Dmail.username="${MAIL_USERNAME}" \' \
    '    -Dmail.password="${MAIL_PASSWORD}" \' \
    '    -Dmail.smtp.host="${MAIL_SMTP_HOST}" \' \
    '    -Dmail.smtp.port="${MAIL_SMTP_PORT}" \' \
    '    -Dws.port="${WS_PORT}" \' \
    '    -Dinstance.id="${INSTANCE_ID:-$(hostname)}"' \
    > /app/start.sh && chmod +x /app/start.sh

EXPOSE 8080 5900
ENTRYPOINT ["/app/start.sh"]
