create table mart.search_assets (
  id bigint not null,
  body text,
  location geometry,
  fulltext index idx_body (body),
  spatial index idx_location (location)
) engine = InnoDB;
