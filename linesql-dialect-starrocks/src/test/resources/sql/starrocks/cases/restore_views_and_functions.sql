restore database sr_hub snapshot sr_view_backup
from test_repo
on (views ads_user_view, materialized views mv_user_summary, functions mask_phone)
