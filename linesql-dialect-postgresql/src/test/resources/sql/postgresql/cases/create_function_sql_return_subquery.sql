CREATE FUNCTION mart.max_order_amount() RETURNS numeric
LANGUAGE SQL
RETURN (SELECT max(src.amount) FROM ods.orders src WHERE src.active);
