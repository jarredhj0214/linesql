insert into mart.orders partition (p202601) (id, amount)
select id, amount
from app.orders
where dt = '2026-01'
