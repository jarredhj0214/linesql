WITH recent AS (
    SELECT u.id, u.name, u.points, u.active
    FROM ods.users AS u
    WHERE u.region = 'US'
)
FROM recent AS r
INSERT OVERWRITE TABLE mart.user_points (user_id, adjusted_points)
SELECT r.id, r.points + 1 WHERE r.active = true
INSERT INTO TABLE mart.user_names (normalized_name, user_id)
SELECT upper(r.name), r.id WHERE r.points > 0;
