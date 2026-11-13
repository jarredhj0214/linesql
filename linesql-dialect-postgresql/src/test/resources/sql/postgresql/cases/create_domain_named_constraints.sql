create domain mart.score numeric(5,2) default 0 constraint score_present not null constraint score_range check (value between 0 and 100);
