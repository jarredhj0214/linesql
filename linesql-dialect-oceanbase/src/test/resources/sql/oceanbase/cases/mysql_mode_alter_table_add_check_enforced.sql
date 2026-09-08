alter table mart.users
  add constraint chk_age check (age >= 0) enforced
