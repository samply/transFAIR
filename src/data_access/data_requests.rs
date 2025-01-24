use super::models::DataRequest;
use sqlx::SqlitePool;
use tracing::debug;

pub async fn get_all(db_pool: &SqlitePool) -> Result<Vec<DataRequest>, sqlx::Error> {
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, exchange_id, project_id, status as "status: _" FROM data_requests;"#,
    )
    .fetch_all(db_pool)
    .await;

    data_request
}

pub async fn get_by_id(db_pool: &SqlitePool, id: &str) -> Result<Option<DataRequest>, sqlx::Error> {
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, exchange_id, project_id, status as "status: _" FROM data_requests WHERE id = $1;"#,
        id
    )
    .fetch_optional(db_pool)
    .await;

    data_request
}

pub async fn get_by(
    db_pool: &SqlitePool,
    exchange_id: &str,
    project_id: Option<&str>,
) -> Result<Option<DataRequest>, sqlx::Error> {
    let data_request = if project_id.is_some() {
        sqlx::query_as!(
            DataRequest,
            r#"SELECT id, exchange_id, project_id, status as "status: _" FROM data_requests WHERE exchange_id = $1 AND project_id = $2;"#,
            exchange_id, project_id
        )
        .fetch_optional(db_pool)
        .await
    } else {
        sqlx::query_as!(
            DataRequest,
            r#"SELECT id, exchange_id, project_id, status as "status: _" FROM data_requests WHERE exchange_id = $1;"#,
            exchange_id
        )
        .fetch_optional(db_pool)
        .await
    };

    debug!("exchange_id: {exchange_id}, project id: {}, data request: {:?}", project_id.unwrap_or(""), data_request.is_err());
    data_request
}

pub async fn exists(db_pool: &SqlitePool, exchange_id: &str, project_id: Option<&str>) -> bool {
    let data_request = get_by(db_pool, exchange_id, project_id).await.unwrap_or(None);
    data_request.is_some()
}

pub async fn insert(db_pool: &SqlitePool, data_request: &DataRequest) -> Result<i64, sqlx::Error> {
    let query = if data_request.project_id.is_some() {
        sqlx::query!(
            "INSERT INTO data_requests (id, exchange_id, project_id, status) VALUES ($1, $2, $3, $4)",
            data_request.id,
            data_request.exchange_id,
            data_request.project_id,
            data_request.status
        )
    } else {
        sqlx::query!(
            "INSERT INTO data_requests (id, exchange_id, status) VALUES ($1, $2, $3)",
            data_request.id,
            data_request.exchange_id,
            data_request.status
        )
    };

    let insert_query_result = query
        .execute(db_pool)
        .await
        .map(|qr| qr.last_insert_rowid());

    insert_query_result
}
