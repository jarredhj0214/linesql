WITH active AS (SELECT a.user_id, a.region FROM live.users a),
     history AS (SELECT h.user_id, h.closed_at FROM archive.users h)
SELECT * FROM active NATURAL LEFT JOIN history ORDER BY 1, 3;
