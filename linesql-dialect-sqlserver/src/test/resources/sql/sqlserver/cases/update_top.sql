update top (100) ads.user_summary
set retry_count = retry_count + 1
where status = 'PENDING';
