select try_cast(o.amount_text as decimal(18, 2)) as amount
from dbo.orders o
where try_cast(o.created_text as date) >= '2026-09-01';
