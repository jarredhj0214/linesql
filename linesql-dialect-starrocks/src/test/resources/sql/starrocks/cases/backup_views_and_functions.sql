backup database sr_hub snapshot sr_view_backup
to test_repo
on (view ads_user_view, materialized view mv_user_summary, function mask_phone)
