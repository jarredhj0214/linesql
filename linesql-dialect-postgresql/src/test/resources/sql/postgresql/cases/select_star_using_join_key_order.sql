WITH active AS (SELECT a.user_id, a.state_id, a.active_at FROM live.users a),
     history AS (SELECT h.user_id, h.state_id, h.closed_at FROM archive.users h)
SELECT * FROM active FULL JOIN history USING (state_id, user_id)
ORDER BY 1, 4;
