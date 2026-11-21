-- accounts module: guests who signed up with a stay code (RIA-51, ADR 0010).
-- Credentials live in Keycloak; no password is stored here.

create table guest (
    id          uuid primary key,
    keycloak_id varchar(36)  not null,
    stay_id     uuid         not null references stay (id),
    email       varchar(254) not null,
    locale      varchar(8)   not null,
    created_at  timestamptz  not null default now(),
    constraint uq_guest_keycloak_id unique (keycloak_id),
    constraint uq_guest_email unique (email),
    -- One account per stay code (decided by the Product Owner for RIA-51).
    constraint uq_guest_stay_id unique (stay_id),
    constraint ck_guest_locale check (locale in ('pt', 'en'))
);
