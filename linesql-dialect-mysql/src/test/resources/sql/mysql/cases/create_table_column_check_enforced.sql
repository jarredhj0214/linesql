create table mart.products (
  id bigint primary key,
  price decimal(10, 2) constraint chk_price_positive check (price >= 0) enforced,
  discount decimal(10, 2) check (discount >= 0) not enforced
);
