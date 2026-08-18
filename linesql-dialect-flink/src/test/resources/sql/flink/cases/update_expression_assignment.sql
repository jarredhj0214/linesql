update ads_user_summary
set user_name = upper(name),
    order_score = order_count + bonus_count
where dt = '20260101';
