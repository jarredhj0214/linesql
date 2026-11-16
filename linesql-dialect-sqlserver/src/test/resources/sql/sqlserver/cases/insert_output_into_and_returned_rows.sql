INSERT INTO dbo.orders (id)
OUTPUT inserted.id INTO audit.insertions (order_id)
OUTPUT inserted.id AS inserted_id
SELECT s.id FROM staging.orders s WHERE s.active = 1;
