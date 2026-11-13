alter type mart.customer_code add attribute nickname text collate "C", alter attribute id set data type numeric(20), drop attribute if exists obsolete restrict;
