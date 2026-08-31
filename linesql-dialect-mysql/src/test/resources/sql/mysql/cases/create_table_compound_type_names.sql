create table mart.type_samples (
  id bigint primary key,
  score double precision,
  display_name national varchar(128),
  short_name national character varying(64),
  alias_name nvarchar(64)
) engine = InnoDB;
