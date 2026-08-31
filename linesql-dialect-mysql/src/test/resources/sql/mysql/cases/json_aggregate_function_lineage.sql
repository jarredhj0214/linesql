select user_id,
       json_arrayagg(json_object('sku', sku, 'qty', quantity) order by created_at) as items_json,
       json_objectagg(sku, amount) as amount_by_sku
from app.order_items
where deleted = 0
group by user_id
