create index idx_user_name_ngram on mart.users (user_name)
using ngrambf
("gram_num" = "3", "bf_size" = "256")
comment "ngram bloom filter index";
