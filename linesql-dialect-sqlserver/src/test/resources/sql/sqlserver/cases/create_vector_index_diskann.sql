CREATE VECTOR INDEX documents_vec_idx ON dbo.documents (embedding)
WITH (METRIC = 'cosine', TYPE = 'DiskANN', MAXDOP = 2) ON [PRIMARY];
