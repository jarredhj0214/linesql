INSERT INTO "audit schema"."order log" ("order ""id""")
SELECT s."order id"
FROM "sales schema"."order source" AS s
WHERE s."status flag" = 1;
