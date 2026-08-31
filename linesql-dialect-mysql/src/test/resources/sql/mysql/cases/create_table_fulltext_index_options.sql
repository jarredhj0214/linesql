create table cms.article_search (
  id bigint not null,
  title varchar(255),
  body text,
  fulltext index ft_article (title, body) with parser ngram comment 'article search' invisible
) engine = InnoDB default charset = utf8mb4;
