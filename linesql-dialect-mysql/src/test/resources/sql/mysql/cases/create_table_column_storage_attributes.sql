create table mart.geo_points (
  id bigint not null,
  location point srid 4326,
  payload varchar(255) column_format dynamic storage disk
) engine = InnoDB;
