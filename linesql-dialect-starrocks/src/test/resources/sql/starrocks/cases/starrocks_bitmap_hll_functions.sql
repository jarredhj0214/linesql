select
  bitmap_union(to_bitmap(user_id)) as user_bitmap,
  hll_union(hll_hash(device_id)) as device_hll
from dwd.user_events;
