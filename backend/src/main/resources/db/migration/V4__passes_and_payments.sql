-- passes and payments modules: base prices, passes and their payments (RIA-52).
-- V4, not V3: V3 is the hub table from RIA-56, which may merge first.

-- BR3: one base price and deposit per pass type, for the whole hotel group (ADR 0013).
create table pass_price (
    pass_type     varchar(16) primary key,
    price_cents   integer     not null,
    deposit_cents integer     not null,
    updated_at    timestamptz not null default now(),
    constraint ck_pass_price_type check (pass_type in ('ONE_DAY', 'THREE_DAY', 'STAY')),
    constraint ck_pass_price_positive check (price_cents > 0 and deposit_cents > 0)
);

-- Provisional values (Product Owner decision, RIA-52) until the backoffice sets the real ones (RIA-121).
insert into pass_price (pass_type, price_cents, deposit_cents) values
    ('ONE_DAY', 1000, 5000),
    ('THREE_DAY', 2500, 5000),
    ('STAY', 4000, 5000);

create table pass (
    id             uuid primary key,
    guest_id       uuid        not null references guest (id),
    pass_type      varchar(16) not null,
    status         varchar(16) not null,
    price_cents    integer     not null,
    deposit_cents  integer     not null,
    deposit_status varchar(16) not null,
    activated_at   timestamptz,
    expires_at     timestamptz,
    created_at     timestamptz not null default now(),
    constraint ck_pass_type check (pass_type in ('ONE_DAY', 'THREE_DAY', 'STAY')),
    constraint ck_pass_status check (status in ('INACTIVE', 'ACTIVE', 'CLOSED')),
    constraint ck_pass_deposit_status check (deposit_status in ('HELD')),
    constraint ck_pass_amounts check (price_cents > 0 and deposit_cents > 0)
);

-- One open pass per guest (Product Owner decision, RIA-52); the final guard against a concurrent purchase.
create unique index uq_pass_open_per_guest on pass (guest_id) where status in ('INACTIVE', 'ACTIVE');

create table payment (
    id                 uuid primary key,
    pass_id            uuid         not null references pass (id),
    kind               varchar(16)  not null,
    amount_cents       bigint       not null,
    status             varchar(16)  not null,
    provider_ref       varchar(255) not null,
    idempotency_key    varchar(255) not null,
    customer_ref       varchar(255),
    payment_method_ref varchar(255),
    created_at         timestamptz  not null default now(),
    -- No duplicate charge (architecture, section 7): the key is also sent to Stripe.
    constraint uq_payment_idempotency_key unique (idempotency_key),
    constraint ck_payment_kind check (kind in ('PURCHASE')),
    constraint ck_payment_status check (status in ('SUCCEEDED')),
    constraint ck_payment_amount check (amount_cents > 0)
);

create index ix_pass_guest_id on pass (guest_id);
create index ix_payment_pass_id on payment (pass_id);
