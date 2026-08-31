select json_value(payload, '$.vehicle.vin' returning char(32) default 'UNKNOWN' on empty null on error) as vin
from app.events;
