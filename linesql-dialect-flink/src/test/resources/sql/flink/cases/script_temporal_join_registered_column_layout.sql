CREATE TABLE ods.rates (
  symbol STRING, rate DECIMAL(10, 2), event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND,
  PRIMARY KEY (symbol) NOT ENFORCED
) WITH ('connector' = 'kafka');
SELECT r.*, o.trade_id
FROM ods.trades o
LEFT JOIN ods.rates FOR SYSTEM_TIME AS OF o.event_time AS r(rate_symbol, exchange_rate, quote_time)
ON o.symbol = r.rate_symbol
ORDER BY 2;
