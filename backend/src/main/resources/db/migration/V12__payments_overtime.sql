-- payments module: overtime charged off-session after a trip (RIA-88, BR7). A charge the provider
-- refused for good is kept as FAILED, without a provider reference, so it is never tried again.
alter table payment drop constraint ck_payment_kind;
alter table payment add constraint ck_payment_kind check (kind in ('PURCHASE', 'OVERTIME'));

alter table payment drop constraint ck_payment_status;
alter table payment add constraint ck_payment_status check (status in ('SUCCEEDED', 'FAILED'));

alter table payment alter column provider_ref drop not null;
alter table payment add constraint ck_payment_provider_ref
    check (status = 'FAILED' or provider_ref is not null);
