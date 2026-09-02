FROM (SELECT month, value FROM dwd.monthly_orders) t
INSERT OVERWRITE TABLE ads.first_half SELECT value WHERE month <= 6
INSERT OVERWRITE TABLE ads.second_half SELECT value WHERE month > 6
