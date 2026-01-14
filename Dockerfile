FROM eclipse-temurin:17-jre

RUN groupadd -g 10001 scim && \
    useradd -r -u 10001 -g scim scim

WORKDIR /app

# Copy the JAR
COPY scimserver.jar /app/scimserver.jar

# BEGIN: Copy custom attribute mappings config
COPY config/scim-attribute-mappings.json /app/config/scim-attribute-mappings.json
# END: Copy custom attribute mappings config

RUN chown -R scim:scim /app

USER scim

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# BEGIN: Add default config file path
ENV PORT=8080 \
    JAVA_OPTS="-Xmx512m -Xms256m" \
    SCIM_CUSTOM_ATTRIBUTE_MAPPINGS_FILE="/app/config/scim-attribute-mappings.json"
# END: Add default config file path

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/scimserver.jar"]