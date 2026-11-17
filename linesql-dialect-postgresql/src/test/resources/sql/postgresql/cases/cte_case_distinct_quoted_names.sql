WITH "Active" AS (SELECT id, amount FROM ods.active_orders),
     active AS (SELECT id, amount FROM ods.historical_orders)
SELECT "Active".id AS active_id, active.amount AS past_amount
FROM "Active" JOIN active ON "Active".id = active.id
WHERE "Active".amount > 0
ORDER BY active.amount;
