SELECT order_id
FROM app.order_segments
WHERE JSON_OVERLAPS(segment_codes, allowed_segments)
  AND JSON_VALID(segment_codes);
