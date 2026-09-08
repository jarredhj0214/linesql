create table mart.user_index_options (
  id bigint unsigned not null,
  tenant_id bigint not null,
  email varchar(128) not null,
  primary key using btree (id) key_block_size = 8,
  unique key uk_tenant_email using btree (tenant_id, email) comment 'tenant email key' visible
) engine = InnoDB;
