alter table cms.articles
  add fulltext index ft_body (title, body) with parser ngram comment 'article search' visible;
