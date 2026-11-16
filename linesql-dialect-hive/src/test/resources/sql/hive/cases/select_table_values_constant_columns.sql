SELECT v.id, v.letter
FROM TABLE(VALUES (1, 'a'), (2, 'b')) AS v(id, letter);
