select
  e.id,
  array_length(e.tags) as tag_count,
  array_intersect(e.tags, e.active_tags) as active_tags
from ods.events e
where array_contains_all(e.tags, ['vip', 'paid'])
