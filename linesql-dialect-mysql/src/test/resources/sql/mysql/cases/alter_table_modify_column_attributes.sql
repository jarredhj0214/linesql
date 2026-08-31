alter table mart.users
modify column email varchar(256) not null default '' comment 'masked email' after name;
