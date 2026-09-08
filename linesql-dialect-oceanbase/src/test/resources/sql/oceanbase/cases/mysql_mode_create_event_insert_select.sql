CREATE EVENT mart.e_daily_orders
ON SCHEDULE EVERY 1 DAY
DO INSERT INTO mart.daily_orders(order_id)
SELECT id FROM app.orders WHERE dt = current_date;
