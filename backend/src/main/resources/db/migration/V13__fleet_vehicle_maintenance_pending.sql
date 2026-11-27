-- fleet module: a fault reported on a bike in use flags it, and the trip end moves it to
-- maintenance instead of available (RIA-92; architecture, section 5, Faults).
alter table vehicle add column maintenance_pending boolean not null default false;

-- Only a bike in use waits for its trip to end.
alter table vehicle add constraint ck_vehicle_maintenance_pending
    check (not maintenance_pending or status = 'IN_USE');
