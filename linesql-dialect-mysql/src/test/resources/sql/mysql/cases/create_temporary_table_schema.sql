create temporary table if not exists mart.tmp_users (
  id bigint not null,
  name varchar(128),
  primary key (id)
) engine = InnoDB row_format = dynamic
