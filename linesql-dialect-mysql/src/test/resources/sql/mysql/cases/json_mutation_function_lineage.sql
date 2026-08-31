select
  json_set(payload, '$.vin', vin, '$.status', status) as enriched_payload,
  json_merge_patch(payload, patch_doc) as merged_payload,
  json_remove(payload, '$.debug') as cleaned_payload
from app.events
