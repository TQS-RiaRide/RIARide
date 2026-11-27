-- passes module: a pass ends no later than the guest's stay (RIA-87, BR2). The stay ends at the end
-- of its check-out day in Vila Ria (Product Owner decision, RIA-87), kept on the pass at purchase.
alter table pass add column stay_ends_at timestamptz;

update pass
set stay_ends_at = (s.check_out + 1)::timestamp at time zone 'Europe/Lisbon'
from guest g
join stay s on s.id = g.stay_id
where g.id = pass.guest_id;

alter table pass alter column stay_ends_at set not null;
