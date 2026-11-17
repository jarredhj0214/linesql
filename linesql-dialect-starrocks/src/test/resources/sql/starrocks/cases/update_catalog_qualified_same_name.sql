UPDATE default_catalog.mart.orders SET total = historical.mart.orders.amount
FROM historical.mart.orders
WHERE default_catalog.mart.orders.id = historical.mart.orders.id;
