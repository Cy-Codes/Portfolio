# ==============================================================================
# Portfolio One-Shot Flyway Migrator Image
# ==============================================================================
FROM flyway/flyway:11.8-alpine

LABEL org.opencontainers.image.title="portfolio-db-migrator" \
      org.opencontainers.image.description="One-shot Flyway migrator for Portfolio PostgreSQL 16 schemas" \
      org.opencontainers.image.source="https://github.com/cy/Portfolio"

COPY --chown=flyway:flyway server/server-db/resources/db/migrations /flyway/sql

USER flyway
ENTRYPOINT ["flyway"]
CMD ["migrate"]
