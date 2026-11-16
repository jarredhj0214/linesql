SELECT c.day_id, s.amount
FROM dims.calendar c
LEFT OUTER JOIN facts.sales s PARTITION BY (s.region)
ON c.day_id = s.day_id;
