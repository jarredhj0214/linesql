restore snapshot example_db.snapshot_label2
from example_repo
on (example_tbl as restored_tbl)
properties ("backup_timestamp" = "2026-08-28-12-00-00")
