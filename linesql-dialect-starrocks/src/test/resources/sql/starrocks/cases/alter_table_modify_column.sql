alter table mart.users modify column nickname varchar(256) not null default '' comment 'normalized display name' after name
