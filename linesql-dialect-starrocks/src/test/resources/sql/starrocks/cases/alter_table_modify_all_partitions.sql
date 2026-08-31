alter table mart.site_access
modify partition (*) set ("storage_medium" = "SSD")
