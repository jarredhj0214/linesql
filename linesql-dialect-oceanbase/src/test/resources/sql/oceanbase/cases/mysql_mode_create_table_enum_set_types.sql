create table mart.user_preferences (
  id bigint not null,
  status enum('ACTIVE', 'DISABLED', 'PENDING') not null,
  channels set('EMAIL', 'SMS', 'PUSH') default 'EMAIL',
  primary key (id)
) engine = InnoDB;
