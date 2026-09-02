restore snapshot sr_member_backup
from test_repo
database sr_hub as sr_hub_new
on (table sr_member as sr_member_new)
properties ("backup_timestamp" = "2024-12-09-10-52-10-940")
