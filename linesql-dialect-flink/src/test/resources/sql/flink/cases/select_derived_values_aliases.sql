SELECT v.user_id, v.status FROM (VALUES (1, 'active'), (2, 'queued')) AS v(user_id, status);
