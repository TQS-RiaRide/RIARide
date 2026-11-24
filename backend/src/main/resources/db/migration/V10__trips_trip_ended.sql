-- trips module: a trip in use ends at a hub with a free slot (RIA-55, BR10). V9 is left to
-- RIA-87, which adds V9__passes_stay_ends_at on its own branch.
alter table trip drop constraint ck_trip_status;
alter table trip add constraint ck_trip_status check (status in ('STARTING', 'IN_USE', 'FAILED', 'ENDED'));

alter table trip add column ended_at timestamptz;
-- The hub the bike was docked at when the trip ended.
alter table trip add column end_hub_id uuid references hub (id);

-- An ended trip has both, and no other trip has either.
alter table trip add constraint ck_trip_ended check (
    (status = 'ENDED') = (ended_at is not null and end_hub_id is not null)
    and (ended_at is null) = (end_hub_id is null)
);
