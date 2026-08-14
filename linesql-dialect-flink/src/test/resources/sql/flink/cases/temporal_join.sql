SELECT o.id, o.amount, o.currency, r.rate, o.amount * r.rate AS converted
FROM ods.orders o
JOIN ods.currency_rates FOR SYSTEM_TIME AS OF o.proc_time AS r
ON o.currency = r.currency
