SELECT i.embedding <=> q.embedding AS cosine_distance,
       i.embedding <#> q.embedding AS inner_product,
       i.embedding <+> q.embedding AS l1_distance,
       i.signature <~> q.signature AS hamming_distance,
       i.signature <%> q.signature AS jaccard_distance
FROM search.items i JOIN search.queries q ON i.category = q.category;
