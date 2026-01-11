FROM eclipse-temurin:17-jre

RUN groupadd -g 10001 scim && \
    useradd -r -u 10001 -g scim scim

WORKDIR /app

# Copy the explicitly named shaded JAR
COPY scimserver.jar /app/scimserver.jar
RUN ls -lh /app/scimserver.jar

RUN chown -R scim:scim /app

USER scim

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENV PORT=8080 \
    JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/scimserver.jar"]
