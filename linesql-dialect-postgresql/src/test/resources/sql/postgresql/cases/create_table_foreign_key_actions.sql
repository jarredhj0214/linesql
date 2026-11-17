CREATE TABLE mart.order_items (
    tenant_id bigint,
    order_id bigint,
    user_id bigint CONSTRAINT user_link REFERENCES app.users (id)
        ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT order_link FOREIGN KEY (tenant_id, order_id)
        REFERENCES sales.orders (tenant_id, id) MATCH SIMPLE
        ON DELETE SET NULL (order_id) ON UPDATE CASCADE
        DEFERRABLE INITIALLY DEFERRED
);
