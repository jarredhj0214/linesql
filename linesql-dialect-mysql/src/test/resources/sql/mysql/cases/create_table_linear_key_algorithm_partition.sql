create table mart.user_shards (
  id bigint not null,
  name varchar(64),
  primary key (id)
)
partition by linear key algorithm = 2 (id) partitions 16;
