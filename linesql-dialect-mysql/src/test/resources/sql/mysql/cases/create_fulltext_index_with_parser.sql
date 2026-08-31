create fulltext index idx_articles_body
on cms.articles(body)
with parser ngram
comment 'article body tokenizer'
invisible;
