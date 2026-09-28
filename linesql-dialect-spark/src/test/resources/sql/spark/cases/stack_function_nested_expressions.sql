select stack(
  2,
  upper(primary_type),
  coalesce(primary_value, ''),
  lower(secondary_type),
  coalesce(secondary_value, '')
) as (kind, value)
from ods.events;
