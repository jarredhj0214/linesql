select
  t.user_id,
  tag.tag_name
from dwd.user_events t
cross join unnest(t.tags) as tag(tag_name);
