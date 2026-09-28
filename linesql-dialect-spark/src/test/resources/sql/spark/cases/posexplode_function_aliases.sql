with date_dim as (
  select date_sub(base_date, seq) as d
  from (
    select base_date, posexplode(offsets) as (seq, x)
    from ods.seed_rows
  ) t
)
select d
from date_dim
