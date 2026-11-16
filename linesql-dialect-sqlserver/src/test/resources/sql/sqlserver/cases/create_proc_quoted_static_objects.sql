CREATE PROC "ops schema"."load.orders" AS
BEGIN
    INSERT INTO "mart schema"."order.log"
    SELECT "FROM ghost" FROM "src schema"."orders.v2";
END;
