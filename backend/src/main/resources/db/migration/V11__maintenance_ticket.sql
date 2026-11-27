-- maintenance module: tickets for faulty bikes, each with an SLA deadline (RIA-92).
-- V9 is unused. #81 (RIA-87) and #82 (RIA-88) also add V11 to V13: whichever of them and this
-- story merges second renumbers its migrations to the next free versions.
-- technician_id, resolved_at, trip_id, pass_id and fault_message_id (architecture, data model)
-- arrive with the stories that use them (RIA-93, RIA-94, RIA-97).

create table maintenance_ticket (
    id          uuid primary key,
    vehicle_id  uuid         not null references vehicle (id),
    source      varchar(16)  not null,
    status      varchar(16)  not null,
    description varchar(500) not null,
    opened_at   timestamptz  not null,
    -- opened_at plus the SLA (riaride.maintenance.sla, 24 h).
    sla_due_at  timestamptz  not null,
    constraint ck_maintenance_ticket_source check (source in ('OPERATOR')),
    constraint ck_maintenance_ticket_status check (status in ('OPEN')),
    constraint ck_maintenance_ticket_description check (length(trim(description)) > 0),
    constraint ck_maintenance_ticket_deadline check (sla_due_at > opened_at)
);

create index ix_maintenance_ticket_vehicle_id on maintenance_ticket (vehicle_id);
-- The SLA breach alert (RIA-95) looks for open tickets past their deadline.
create index ix_maintenance_ticket_status_sla_due_at on maintenance_ticket (status, sla_due_at);
