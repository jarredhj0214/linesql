CREATE TABLE app.documents (
    id NUMBER,
    embedding VECTOR(384, FLOAT32, DENSE),
    sparse_embedding VECTOR(*, FLOAT64, SPARSE)
);
