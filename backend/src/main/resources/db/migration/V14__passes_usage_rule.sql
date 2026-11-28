-- passes module: the rules of use a hotel manager sets from the backoffice (RIA-96, architecture
-- section 7). A change adds a row from the moment it is made and never edits one, so a trip keeps
-- the rule in force when it started, and its overtime, if retried later, comes out the same.
create table usage_rule (
    id                        uuid        primary key,
    max_trip_duration_minutes integer     not null,
    effective_from            timestamptz not null,
    constraint uq_usage_rule_effective_from unique (effective_from),
    -- From a quarter of an hour (the overtime block) to a day.
    constraint ck_usage_rule_max_trip_duration check (max_trip_duration_minutes between 15 and 1440)
);

-- BR7: 3 h until a hotel manager changes it (the value of riaride.trips.overtime.max-trip-duration
-- before RIA-96).
insert into usage_rule (id, max_trip_duration_minutes, effective_from)
values ('00000000-0000-0000-0000-000000000096', 180, timestamptz '1970-01-01 00:00:00+00');
