select id
from app.orders
where (user_id, product_id) = (buyer_id, sku_id);
