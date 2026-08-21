create table mart.account_policy (
  id bigint not null,
  amount decimal(18, 2) check (amount >= 0),
  secret_note varchar(255) invisible,
  constraint chk_amount check (amount < 1000000),
  index idx_amount (amount) invisible
) engine = InnoDB;
