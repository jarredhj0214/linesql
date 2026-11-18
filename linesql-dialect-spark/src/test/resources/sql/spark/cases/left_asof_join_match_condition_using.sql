SELECT symbol, t.trade_time, q.bid_price AS attached_bid
FROM ods.trades t LEFT ASOF JOIN ods.quotes q
MATCH_CONDITION (t.trade_time >= q.quote_time) USING (symbol)
WHERE symbol IS NOT NULL
