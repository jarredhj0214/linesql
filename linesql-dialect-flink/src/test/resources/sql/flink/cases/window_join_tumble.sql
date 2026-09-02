SELECT l.window_start, l.user_id, r.amount
FROM TABLE(TUMBLE(TABLE ods.clicks, DESCRIPTOR(ts), INTERVAL '5' MINUTE)) AS l(window_start, window_end, user_id)
JOIN TABLE(TUMBLE(TABLE dwd.payments, DESCRIPTOR(ts), INTERVAL '5' MINUTE)) AS r(window_start, window_end, user_id, amount)
ON l.window_start = r.window_start
AND l.window_end = r.window_end
AND l.user_id = r.user_id
