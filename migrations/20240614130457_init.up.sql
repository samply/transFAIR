CREATE TABLE IF NOT EXISTS request_status (
    type    CHAR(16)    PRIMARY KEY NOT NULL,
    seq     INTEGER 
);

INSERT OR IGNORE INTO request_status(type, seq) 
VALUES  ('Created', 1), 
        ('DataLoaded', 2), 
        ('UpdateAvailable', 3), 
        ('Error', 4);

CREATE TABLE IF NOT EXISTS data_requests (
    id          CHAR(36)    PRIMARY KEY NOT NULL,
    project_id  CHAR(36)    NOT NULL,
    status      CHAR(16)    NOT NULL DEFAULT ('Created') REFERENCES request_status(Type),
    UNIQUE(id, project_id)
)
