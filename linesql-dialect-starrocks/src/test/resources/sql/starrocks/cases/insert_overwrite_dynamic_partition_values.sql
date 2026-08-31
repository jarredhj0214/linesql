insert overwrite mart.activity
partition(id = '4', dt = '2026-08-28')
with label insert_activity_auto_partition
values ('4', '2026-08-28')
