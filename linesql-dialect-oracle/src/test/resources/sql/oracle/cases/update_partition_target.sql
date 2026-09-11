update mart.user_summary partition (p202609) t
set user_name = upper(t.user_name)
where t.status = 'ACTIVE';
