alter event mart.daily_rollup
  on schedule every 1 day
  do insert into mart.order_daily select dt, count(*) from app.orders group by dt;
