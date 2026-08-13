merge into ads.users t using ods.users s on t.id = s.id
when matched then update set *
when not matched then insert *
