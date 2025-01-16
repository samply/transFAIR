CREATE TABLE IF NOT EXISTS request_status (
    type    CHAR(16)    PRIMARY KEY NOT NULL,
    seq     INTEGER 
);

INSERT OR IGNORE INTO request_status(type, seq) 
VALUES  ('Created', 1), 
        ('Success', 2),
        ('Error', 3);

CREATE TABLE IF NOT EXISTS data_requests (
    id      CHAR(36)    PRIMARY KEY NOT NULL,
    exchange_id CHAR(36) NOT NULL,
    project_id CHAR(36),
    status  CHAR(16)    NOT NULL DEFAULT ('Created') REFERENCES request_status(Type),
    message TEXT
    UNIQUE(patient_id, project_id)
);

CREATE TABLE IF NOT EXISTS _data_requests_history (
    _rowid INTEGER,
    id CHAR(36),
    exchange_id CHAR(36),
    project_id CHAR(36),
    status CHAR(16),
    message TEXT,
    _version INTEGER,
    _updated INTEGER
);

CREATE INDEX idx_data_requests_history_rowid ON _data_requests_history (_rowid);

CREATE TRIGGER data_requests_insert_history
AFTER INSERT ON data_requests
BEGIN
    INSERT INTO _data_requests_history (_rowid, id, exchange_id, project_id, status, message, _version, _updated)
    VALUES (new.rowid, new.id, new.exchange_id, new.project_id, new.status, new.message, 1, cast((julianday('now') - 2440587.5) * 86400 * 1000 as integer));
END;

CREATE TRIGGER data_requests_update_history
AFTER UPDATE ON data_requests
FOR EACH ROW
BEGIN
    INSERT INTO _data_requests_history (_rowid, id, exchange_id, project_id, status, message, _version, _updated)
    SELECT old.rowid,
        CASE WHEN old.id != new.id then new.id else null end,
        CASE WHEN old.exchange_id != new.exchange_id then new.exchange_id else null end,
        CASE WHEN old.project_id != new.project_id then new.project_id else null end,
        CASE WHEN old.status != new.status then new.status else null end,
        CASE WHEN old.message != new.message then new.message else null end,
        (SELECT MAX(_version) FROM _data_requests_history WHERE _rowid = old.rowid) + 1,
        cast((julianday('now') - 2440587.5) * 86400 * 1000 as integer)
    WHERE old.id != new.id or old.status != new.status or old.message != new.message;
END;
