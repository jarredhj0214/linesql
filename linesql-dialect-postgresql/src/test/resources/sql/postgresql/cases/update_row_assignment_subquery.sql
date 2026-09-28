update mart.user_summary t
set (user_name, order_total) = (
  select s.name, sum(o.amount)
  from staging.users s
  join sales.orders o on o.user_id = s.id
  where s.id = t.user_id
  group by s.name
)
where t.status = 'ACTIVE'
returning t.user_id;
