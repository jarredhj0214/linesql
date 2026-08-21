update mart.users u, app.orders o
set u.last_order_amount = o.amount,
    o.synced = 1
where u.id = o.user_id
  and o.status = 'PAID'
