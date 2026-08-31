select order_id
from app.order_segments
where json_overlaps(segment_codes, allowed_segments)
  and json_valid(segment_codes)
