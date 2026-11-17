INSERT INTO audit.summary(customer, region, last_seen)
WITH active AS (SELECT a.user_id, a.region FROM live.users a),
     history AS (SELECT h.user_id, h.closed_at FROM archive.users h)
SELECT * FROM active RIGHT JOIN history USING (user_id);
