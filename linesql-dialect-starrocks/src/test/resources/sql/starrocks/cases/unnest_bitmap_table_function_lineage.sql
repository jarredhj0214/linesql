select
  l.id,
  b.unnest as user_id
from ods.bitmap_logs l, unnest_bitmap(l.user_bitmap) b
