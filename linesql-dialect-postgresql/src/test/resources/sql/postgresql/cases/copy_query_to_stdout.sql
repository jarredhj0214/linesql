copy (
  select u.id as user_id, o.amount
  from mart.users u
  join mart.orders o on u.id = o.user_id
  where u.status = 'ACTIVE'
) to stdout with csv header;
