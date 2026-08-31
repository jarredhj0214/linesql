create table mart.session_archive (
  id bigint primary key,
  payload text
)
engine = MyISAM
auto_increment = 1000
avg_row_length = 4096
max_rows = 1000000
min_rows = 100
delay_key_write = 1
insert_method = last
union = (archive.session_2025, archive.session_2026)
