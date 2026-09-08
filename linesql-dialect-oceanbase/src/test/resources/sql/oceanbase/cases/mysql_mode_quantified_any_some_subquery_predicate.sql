SELECT o.id
FROM app.orders o
WHERE o.amount = ANY (
    SELECT r.target_amount
    FROM app.region_targets r
    WHERE r.region = o.region
)
AND o.status <> SOME (
    SELECT b.status
    FROM app.blocked_status b
    WHERE b.enabled = 1
);
