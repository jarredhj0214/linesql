create table mart.user_profile_ext (
  id bigint not null auto_increment,
  first_name varchar(64) not null,
  last_name varchar(64) not null,
  full_name varchar(130) generated always as (concat(first_name, ' ', last_name)) stored,
  updated_at timestamp default current_timestamp on update current_timestamp,
  primary key (id)
) engine = InnoDB;
