create table mart.user_tags (
  user_id bigint not null,
  tag varchar(64),
  index idx_tag (tag) using bitmap comment 'tag bitmap index'
)
duplicate key(user_id)
distributed by hash(user_id)
