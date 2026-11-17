ALTER TABLE mart.order_items ADD CONSTRAINT order_link
    FOREIGN KEY (tenant_id, order_id) REFERENCES sales.orders (tenant_id, id)
    ON DELETE NO ACTION ON UPDATE CASCADE
    DEFERRABLE INITIALLY IMMEDIATE NOT VALID;
