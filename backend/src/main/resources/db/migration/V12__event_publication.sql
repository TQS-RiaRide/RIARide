-- Spring Modulith event publication registry (architecture, section 5): a publication stays until
-- its listener succeeds, so a crash or a payment outage does not lose an overtime charge (RIA-88).
-- The schema is Spring Modulith 2.1's (spring-modulith-events-jdbc, schemas/v2/schema-postgresql.sql);
-- Flyway owns it, so the library's own schema initialization stays off.
create table event_publication
(
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);
