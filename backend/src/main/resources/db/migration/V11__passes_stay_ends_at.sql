-- passes module: a pass ends no later than the guest's stay (RIA-87, BR2). The stay ends at the end
-- of its check-out day in Vila Ria (Product Owner decision, RIA-87), kept on the pass at purchase.
-- V11, not V9: V10 (RIA-55) reached dev first, and Flyway refuses a migration older than one
-- already applied.
alter table pass add column stay_ends_at timestamptz;

update pass
set stay_ends_at = (s.check_out + 1)::timestamp at time zone 'Europe/Lisbon'
from guest g
join stay s on s.id = g.stay_id
where g.id = pass.guest_id;

alter table pass alter column stay_ends_at set not null;

-- A pass activated before RIA-87 has no expiry: it gets the one its first trip gives now, 24 h or
-- 72 h after activation, never after the end of the stay; a stay pass ends with the stay.
update pass
set expires_at = case pass_type
    when 'ONE_DAY' then least(activated_at + interval '24 hours', stay_ends_at)
    when 'THREE_DAY' then least(activated_at + interval '72 hours', stay_ends_at)
    else stay_ends_at
end
where activated_at is not null and expires_at is null;

-- From now on an active pass always has an expiry.
alter table pass add constraint ck_pass_active_expires
    check (status <> 'ACTIVE' or expires_at is not null);
