create server fed_orders
foreign data wrapper mysql
options (user 'etl', host '127.0.0.1', database 'orders')
