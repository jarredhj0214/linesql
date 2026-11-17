CREATE TABLE mart.order_snapshots (
    LIKE template.order_fields INCLUDING DEFAULTS,
    LIKE template.audit_fields EXCLUDING COMMENTS,
    total numeric GENERATED ALWAYS AS (subtotal + shipping) STORED,
    audit_user bigint REFERENCES ops.users (id),
    CONSTRAINT positive_shipping CHECK (shipping >= 0)
);
