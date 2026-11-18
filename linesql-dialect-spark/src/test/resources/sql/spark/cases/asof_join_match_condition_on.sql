SELECT t.trade_time, q.bid_price AS attached_bid
FROM ods.trades t ASOF JOIN ods.quotes q
MATCH_CONDITION (t.trade_time >= q.quote_time) ON t.symbol = q.symbol
