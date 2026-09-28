select q.payload.user.id as user_id,
       q.payload.device.os as device_os
from (
  select from_json(raw_payload, 'struct<user:struct<id:string>,device:struct<os:string>>') as payload
  from ods.events
) q;
