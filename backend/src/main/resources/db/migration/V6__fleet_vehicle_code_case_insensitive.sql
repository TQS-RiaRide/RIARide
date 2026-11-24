-- fleet module: vehicle codes are unique regardless of case (RIA-57), so EB-401 and eb-401 cannot
-- both be registered. The index keeps the constraint's name, which VehicleProblemHandler maps to
-- 409 "Vehicle code already in use".

alter table vehicle drop constraint uq_vehicle_code;

create unique index uq_vehicle_code on vehicle (lower(code));
