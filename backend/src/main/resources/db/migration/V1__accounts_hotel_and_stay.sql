-- accounts module: hotels of the group and the stays they register.

create table hotel (
    id   uuid primary key,
    name text not null,
    constraint uq_hotel_name unique (name)
);

-- The hotels of Grupo Hoteleiro Marés are reference data, needed in every environment.
-- Fixed ids: Keycloak users carry them in the hotel_id attribute.
insert into hotel (id, name) values
    ('00000000-0000-0000-0000-000000000001', 'Marés Centro'),
    ('00000000-0000-0000-0000-000000000002', 'Marés Praia'),
    ('00000000-0000-0000-0000-000000000003', 'Marés Salinas');

create table stay (
    id         uuid primary key,
    hotel_id   uuid        not null references hotel (id),
    guest_name text        not null,
    stay_code  varchar(8)  not null,
    check_in   date        not null,
    check_out  date        not null,
    status     varchar(16) not null,
    created_at timestamptz not null default now(),
    constraint uq_stay_stay_code unique (stay_code),
    constraint ck_stay_check_out_after_check_in check (check_out > check_in),
    constraint ck_stay_status check (status in ('ACTIVE', 'CHECKED_OUT'))
);

create index ix_stay_hotel_id on stay (hotel_id);
