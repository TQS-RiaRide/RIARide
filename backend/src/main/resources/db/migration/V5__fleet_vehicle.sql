-- fleet module: vehicles and their latest known state (RIA-59; registration itself is RIA-57).
-- V5: V3 is the hub table (RIA-56) and V4 the passes and payments (RIA-52).

create table vehicle (
    id                uuid primary key,
    -- The id the simulator uses in its MQTT topics, for example EB-601.
    code              varchar(32) not null,
    type              varchar(16) not null,
    status            varchar(16) not null,
    -- The hub the vehicle is docked at; null while it is ridden.
    hub_id            uuid        references hub (id),
    -- Latest battery reading from telemetry; null until the first one arrives.
    battery_pct       integer,
    last_telemetry_at timestamptz,
    created_at        timestamptz not null default now(),
    constraint uq_vehicle_code unique (code),
    constraint ck_vehicle_type check (type in ('E_BIKE')),
    constraint ck_vehicle_status check (status in ('AVAILABLE', 'RESERVED', 'IN_USE', 'MAINTENANCE', 'RETIRED')),
    constraint ck_vehicle_battery check (battery_pct between 0 and 100)
);

create index ix_vehicle_hub_id on vehicle (hub_id);
