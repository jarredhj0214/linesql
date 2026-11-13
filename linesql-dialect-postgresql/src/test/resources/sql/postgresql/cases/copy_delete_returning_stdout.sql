COPY (
    DELETE FROM mart.expired_sessions AS s
    WHERE s.expires_at < CURRENT_TIMESTAMP
    RETURNING s.id AS session_id
) TO STDOUT WITH (FORMAT csv);
