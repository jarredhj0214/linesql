create table mart.user_bucket (
  id bigint not null,
  user_name varchar(128),
  primary key (id)
)
engine = InnoDB
partition by hash (id) partitions 8
