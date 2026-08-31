show temporary partitions from mart.site_access
where PartitionName like 'tp%'
order by LastConsistencyCheckTime desc
limit 10
