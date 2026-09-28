alter table mart.orders
add constraint positive_total check (amount + tax >= 0) not enforced
