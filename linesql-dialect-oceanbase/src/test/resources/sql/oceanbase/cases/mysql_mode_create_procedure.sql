CREATE PROCEDURE mart.refresh_orders()
BEGIN
  INSERT INTO mart.daily_orders(order_id)
  SELECT id FROM app.orders;
END;
