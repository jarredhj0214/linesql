with cte1 as (
  select id, name from ods.users
),
cte2 as (
  select id, name from cte1
)
select id, name from cte2
