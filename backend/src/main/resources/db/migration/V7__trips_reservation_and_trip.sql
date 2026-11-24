-- trips module: reservations and trips (RIA-54 starts trips; RIA-55 ends them; story 3.3 expires
-- reservations).

-- A bike held for a pass (BR11). A failed start leaves it active, so a retry on the same bike reuses it.
create table reservation (
    id           uuid primary key,
    pass_id      uuid        not null references pass (id),
    vehicle_id   uuid        not null references vehicle (id),
    -- The id the simulator uses in its MQTT topics.
    vehicle_code varchar(32) not null,
    -- The hub the bike was docked at when reserved, where a failed start locks it again.
    hub_id       uuid        references hub (id),
    status       varchar(16) not null,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    constraint ck_reservation_status check (status in ('ACTIVE', 'USED'))
);

-- Two guests cannot hold the same bike, and a pass holds one bike at a time (BR6), even when the
-- requests run at the same time.
create unique index uq_reservation_active_per_vehicle on reservation (vehicle_id) where status = 'ACTIVE';
create unique index uq_reservation_active_per_pass on reservation (pass_id) where status = 'ACTIVE';

create table trip (
    id             uuid primary key,
    guest_id       uuid        not null references guest (id),
    pass_id        uuid        not null references pass (id),
    -- A retry after a failed start reuses the reservation, so several trips may share one.
    reservation_id uuid        not null references reservation (id),
    vehicle_id     uuid        not null references vehicle (id),
    -- The id the simulator uses in its MQTT topics, so telemetry finds the trip without fleet.
    vehicle_code   varchar(32) not null,
    hub_id         uuid        references hub (id),
    status         varchar(16) not null,
    requested_at   timestamptz not null,
    started_at     timestamptz,
    failed_at      timestamptz,
    version        bigint      not null default 0,
    constraint ck_trip_status check (status in ('STARTING', 'IN_USE', 'FAILED'))
);

-- BR6, one vehicle at a time per pass: two concurrent starts for the same pass cannot both succeed.
create unique index uq_trip_active_per_pass on trip (pass_id) where status in ('STARTING', 'IN_USE');

-- The telemetry listener finds a bike's latest trip; the timeout job finds overdue starts; a guest
-- reads their own trips.
create index ix_trip_vehicle_code_requested_at on trip (vehicle_code, requested_at);
create index ix_trip_status_requested_at on trip (status, requested_at);
create index ix_trip_guest_id on trip (guest_id);
