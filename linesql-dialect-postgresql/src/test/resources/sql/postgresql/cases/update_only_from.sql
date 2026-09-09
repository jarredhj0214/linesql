UPDATE ONLY mart.users u
SET status = s.status
FROM staging.users_status s
WHERE u.id = s.id;
