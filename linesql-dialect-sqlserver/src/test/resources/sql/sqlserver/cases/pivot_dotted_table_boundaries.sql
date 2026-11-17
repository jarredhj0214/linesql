SELECT p.[one]
FROM (
  SELECT a.key_value AS sale_value, b.key_value AS region_key
  FROM [sales.region].orders a JOIN sales.[region.orders] b ON a.id = b.id
) d
PIVOT (SUM(sale_value) FOR region_key IN ([one])) AS p;
