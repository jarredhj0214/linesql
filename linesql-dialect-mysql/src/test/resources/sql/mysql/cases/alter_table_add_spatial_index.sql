alter table geo.places
  add spatial index idx_location (location) invisible;
