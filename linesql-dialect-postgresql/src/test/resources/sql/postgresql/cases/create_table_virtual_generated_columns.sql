CREATE TABLE mart.metrics (
    amount numeric,
    discount numeric,
    net numeric GENERATED ALWAYS AS (amount - discount) VIRTUAL,
    double_amount numeric GENERATED ALWAYS AS (amount * 2)
);
