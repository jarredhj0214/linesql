select o.id
from app.orders o
where (o.user_id, o.product_id) in (
    select a.user_id, a.product_id
    from app.allowed_products a
    where a.enabled = 1
)
