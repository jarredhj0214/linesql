select
  json_pretty(payload) as pretty_payload,
  json_type(payload) as payload_type,
  json_depth(payload) as payload_depth,
  json_keys(payload, '$.attrs') as attr_keys,
  json_storage_size(payload) + json_storage_free(payload) as json_storage_bytes
from app.events
where json_valid(payload)

