-- trips module: a reservation whose hold ended unused expires (BR11), and its bike is released.
alter table reservation drop constraint ck_reservation_status;
alter table reservation add constraint ck_reservation_status check (status in ('ACTIVE', 'USED', 'EXPIRED'));

-- ReservationExpiryJob finds the active reservations past their hold.
create index ix_reservation_status_expires_at on reservation (status, expires_at);
