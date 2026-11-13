MERGE INTO ONLY (mart.users) AS t
USING staging.users_delta * AS s ON t.id = s.id
WHEN NOT MATCHED BY SOURCE AND t.expired THEN DELETE
WHEN NOT MATCHED BY SOURCE AND t.active THEN
    UPDATE SET status = t.fallback_status
WHEN NOT MATCHED BY TARGET AND s.active THEN
    INSERT (id, status) OVERRIDING SYSTEM VALUE VALUES (s.id, s.status)
RETURNING t.id AS user_id;
