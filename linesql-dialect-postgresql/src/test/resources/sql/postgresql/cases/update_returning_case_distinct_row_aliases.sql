WITH revised AS (
    UPDATE mart.orders AS "Rates"
    SET amount = rates.amount
    FROM staging.prices AS rates
    WHERE "Rates".id = rates.id
    RETURNING WITH (OLD AS "Version", NEW AS version)
        "Version".amount AS old_amount, version.amount AS new_amount, "Rates".id AS order_id
)
SELECT old_amount, new_amount, order_id FROM revised;
