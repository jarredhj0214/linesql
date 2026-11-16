SELECT o.id,
       (SELECT max(i.amount) + o.adjustment
        FROM sales.items i WHERE i.order_id = o.id) AS total
FROM sales.orders o;
