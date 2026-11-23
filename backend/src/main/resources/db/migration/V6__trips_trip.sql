-- trips module: trips on a vehicle (RIA-54 starts them; RIA-55 ends them).

create table trip (
    id           uuid primary key,
    guest_id     uuid        not null references guest (id),
    pass_id      uuid        not null references pass (id),
    vehicle_id   uuid        not null references vehicle (id),
    -- The id the simulator uses in its MQTT topics, so telemetry finds the trip without fleet.
    vehicle_code varchar(32) not null,
    status       varchar(16) not null,
    requested_at timestamptz not null,
    started_at   timestamptz,
    failed_at    timestamptz,
    version      bigint      not null default 0,
    constraint ck_trip_status check (status in ('STARTING', 'IN_USE', 'FAILED'))
);

-- BR6, one vehicle at a time per pass: two concurrent starts for the same pass cannot both succeed.
create unique index uq_trip_active_per_pass on trip (pass_id) where status in ('STARTING', 'IN_USE');

-- The telemetry listener finds a bike's starting trip; the timeout job finds overdue starts.
create index ix_trip_vehicle_code_status on trip (vehicle_code, status);
create index ix_trip_status_requested_at on trip (status, requested_at);
create index ix_trip_guest_id on trip (guest_id);
