CREATE TABLE mart.project_assignments (
    worker_id bigint,
    project_id bigint,
    valid_at daterange,
    CONSTRAINT assignments_pk PRIMARY KEY (worker_id, project_id, valid_at WITHOUT OVERLAPS),
    CONSTRAINT project_period_fk FOREIGN KEY (project_id, PERIOD valid_at)
        REFERENCES core.project_history (project_id, PERIOD active_at)
);
