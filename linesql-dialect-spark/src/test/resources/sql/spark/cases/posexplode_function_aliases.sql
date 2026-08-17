with date_dim as (
  select date_sub('2026-08-11', seq) as d
  from (
    select posexplode(split(space(2), ' ')) as (seq, x)
    from ods.seed_rows
  ) t
)
select d
from date_dim
