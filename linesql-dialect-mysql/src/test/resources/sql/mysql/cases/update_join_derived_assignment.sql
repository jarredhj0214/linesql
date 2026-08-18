update mart.users u
join (
  select id, name, order_count
  from app.users_delta
) d on u.id = d.id
set u.name = upper(d.name),
    u.score = d.order_count + u.bonus_count
