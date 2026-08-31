delete from app.user_events partition (p202608)
where event_time < '2026-08-01'
limit 1000;
