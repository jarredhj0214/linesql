create table mart.user_login (
  id bigint not null,
  tenant_id bigint not null,
  login_name varchar(128) not null,
  constraint uk_tenant_login unique key (tenant_id, login_name)
) engine = InnoDB;
