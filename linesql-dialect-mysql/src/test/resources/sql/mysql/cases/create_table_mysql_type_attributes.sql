create table mart.user_type_attrs (
  id bigint unsigned not null,
  score decimal(10, 2) unsigned zerofill,
  nickname varchar(64) character set utf8mb4 collate utf8mb4_bin,
  memo text charset utf8mb4
) engine = InnoDB default charset = utf8mb4;
