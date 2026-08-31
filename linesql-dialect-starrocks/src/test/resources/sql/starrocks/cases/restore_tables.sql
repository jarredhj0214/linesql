restore database sr_hub snapshot sr_core_backup
from test_repo
on (table sr_member, table sr_pmc)
properties ("backup_timestamp" = "2026-08-28-12-00-00");
