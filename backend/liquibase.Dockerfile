FROM liquibase:5.0.1-alpine

RUN lpm add postgresql --global

COPY src/main/resources/db/changelog src/main/resources/db/changelog

ENTRYPOINT ["liquibase", "update"]
