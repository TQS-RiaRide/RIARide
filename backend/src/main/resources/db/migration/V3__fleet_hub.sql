-- fleet module: hubs where bikes are docked and trips start and end (RIA-56).

create table hub (
    id         uuid primary key,
    name       varchar(120)     not null,
    kind       text             not null,
    -- The hotel a HOTEL hub belongs to; null for a TOURIST_SPOT hub.
    hotel_id   uuid             references hotel (id),
    -- Position in decimal degrees (WGS 84).
    latitude   double precision not null,
    longitude  double precision not null,
    capacity   integer          not null,
    created_at timestamptz      not null default now(),
    -- Names are compared exactly (decided by the Product Owner for RIA-56). The constraint
    -- stops two concurrent registrations from both taking the same name.
    constraint uq_hub_name unique (name),
    constraint ck_hub_kind check (kind in ('HOTEL', 'TOURIST_SPOT')),
    constraint ck_hub_hotel_matches_kind check ((kind = 'HOTEL') = (hotel_id is not null)),
    constraint ck_hub_latitude check (latitude between -90 and 90),
    constraint ck_hub_longitude check (longitude between -180 and 180),
    -- BR10: a hub has room for at least one bike.
    constraint ck_hub_capacity check (capacity >= 1)
);

create index ix_hub_hotel_id on hub (hotel_id);
