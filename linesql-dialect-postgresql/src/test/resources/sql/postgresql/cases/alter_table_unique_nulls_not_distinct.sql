ALTER TABLE ONLY mart.orders
    ADD CONSTRAINT orders_external_ref UNIQUE NULLS NOT DISTINCT (external_ref) INCLUDE (observed_at)
    DEFERRABLE INITIALLY DEFERRED;
