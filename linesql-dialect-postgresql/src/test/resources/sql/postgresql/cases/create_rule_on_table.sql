create rule orders_update_log as
on update to mart.orders
where old.status <> new.status
do also notify orders_changed
