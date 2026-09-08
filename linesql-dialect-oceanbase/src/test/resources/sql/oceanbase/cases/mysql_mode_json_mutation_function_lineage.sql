SELECT
  JSON_SET(payload, '$.vin', vin, '$.status', status) AS enriched_payload,
  JSON_MERGE_PATCH(payload, patch_doc) AS merged_payload,
  JSON_REMOVE(payload, '$.debug') AS cleaned_payload
FROM app.events;
