alter table mart.sales exchange partition p202609
with table staging.sales_p
including indexes without validation;
