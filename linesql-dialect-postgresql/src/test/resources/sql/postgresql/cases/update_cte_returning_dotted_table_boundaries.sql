WITH updated AS (
  UPDATE "sales.region".orders a SET price = b.retail_price
  FROM sales."region.orders" b
  WHERE a.id = b.id
  RETURNING a.price AS new_price, b.price AS source_price
)
SELECT new_price, source_price FROM updated;
