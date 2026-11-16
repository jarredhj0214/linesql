INSERT INTO mart.holding_prices (holding_id, matched_price)
SELECT h.id, p.price
FROM ods.holdings h ASOF LEFT JOIN ods.prices p
ON h.ticker = p.ticker AND h.observed_at >= p.quoted_at;
