backup database sr_hub snapshot sr_core_backup
to test_repo
on (table sr_member, table sr_pmc)
properties ("type" = "full");
