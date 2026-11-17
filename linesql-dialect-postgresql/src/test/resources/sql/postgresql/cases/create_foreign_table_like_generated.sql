CREATE FOREIGN TABLE ext.inventory (
    LIKE template.inventory_fields INCLUDING DEFAULTS,
    qty integer,
    normalized_qty integer GENERATED ALWAYS AS (abs(qty)) STORED,
    CONSTRAINT nonnegative_qty CHECK (qty >= 0)
) SERVER warehouse_link;
