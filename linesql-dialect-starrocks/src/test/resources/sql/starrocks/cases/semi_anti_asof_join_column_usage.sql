select
  u.id,
  u.name
from ods.users u
left semi join dwd.orders o on u.id = o.user_id
left anti join dwd.refunds r on u.id = r.user_id
asof join dwd.user_snapshots s on u.id = s.user_id and u.event_time >= s.snapshot_time
