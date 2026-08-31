select id
from app.orders
where (user_id, product_id) in ((1, 100), (2, 200));
