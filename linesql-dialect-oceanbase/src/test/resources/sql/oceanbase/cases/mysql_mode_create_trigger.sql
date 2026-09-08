CREATE TRIGGER mart.trg_orders_ai
AFTER INSERT ON mart.orders
FOR EACH ROW
INSERT INTO mart.order_audit(order_id) VALUES (NEW.id);
