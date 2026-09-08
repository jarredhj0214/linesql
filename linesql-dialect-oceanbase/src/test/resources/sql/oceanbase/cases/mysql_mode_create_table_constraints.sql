create table mart.user_profile (
  id bigint not null auto_increment,
  user_name varchar(128) not null,
  email varchar(256),
  created_at datetime default current_timestamp,
  primary key (id),
  unique key uk_email (email),
  index idx_user_name (user_name)
) engine = InnoDB default charset = utf8mb4 comment = 'user profile';
