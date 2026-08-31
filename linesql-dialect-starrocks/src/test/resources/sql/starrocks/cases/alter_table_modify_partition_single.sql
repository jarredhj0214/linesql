alter table mart.site_access
modify partition p20230106 set ("replication_num" = "3")
