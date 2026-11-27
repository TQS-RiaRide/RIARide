-- maintenance module: tickets for faulty bikes, each with an SLA deadline (RIA-92).
-- V11: V9 (passes, RIA-87) and V10 (trips, RIA-55) are taken by other stories.
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
