-- trips module: a reservation whose bike left service because of a fault is cancelled (RIA-92).
alter table reservation drop constraint ck_reservation_status;
alter table reservation add constraint ck_reservation_status check (status in ('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED'));
