select
  l.id,
  u.unnest as tag
from ods.logs l, unnest(l.tags) u
