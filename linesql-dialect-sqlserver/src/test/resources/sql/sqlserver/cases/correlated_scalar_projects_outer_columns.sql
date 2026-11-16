SELECT o.id,
       (SELECT MAX(i.amount + o.adjustment) AS total
        FROM sales.items i WHERE i.order_id = o.id) AS total
FROM sales.orders o;
