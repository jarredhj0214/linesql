SELECT o.id
FROM app.orders o
WHERE (o.user_id, o.product_id) IN (
    SELECT a.user_id, a.product_id
    FROM app.allowed_products a
    WHERE a.enabled = 1
);
