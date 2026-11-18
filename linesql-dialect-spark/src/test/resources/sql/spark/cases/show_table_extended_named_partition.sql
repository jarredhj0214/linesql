SHOW TABLE EXTENDED FROM mart LIKE 'orders'
PARTITION (dt = '2026-09-14', region = 'eu')
