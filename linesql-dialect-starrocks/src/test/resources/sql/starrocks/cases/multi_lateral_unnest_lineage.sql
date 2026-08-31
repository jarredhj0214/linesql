select
  t.id,
  tag.unnest as tag,
  flag.unnest as flag
from ods.logs t, unnest(t.tags) tag, unnest(t.flags) flag
