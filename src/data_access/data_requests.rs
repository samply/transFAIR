use super::models::DataRequest;
use sqlx::SqlitePool;
use tracing::debug;

pub async fn get_all(db_pool: &SqlitePool) -> Result<Vec<DataRequest>, sqlx::Error> {
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, patient_id, project_id, status as "status: _" FROM data_requests;"#,
    )
    .fetch_all(db_pool)
    .await;

    data_request
}

pub async fn get_by_id(db_pool: &SqlitePool, id: &str) -> Result<Option<DataRequest>, sqlx::Error> {
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, patient_id, project_id, status as "status: _" FROM data_requests WHERE id = $1;"#,
        id
    )
    .fetch_optional(db_pool)
    .await;

    data_request
}

pub async fn get_by(
    db_pool: &SqlitePool,
    patient_id: &str,
    project_id: &str,
) -> Result<Option<DataRequest>, sqlx::Error> {
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, patient_id, project_id, status as "status: _" FROM data_requests WHERE patient_id = $1 AND project_id = $2;"#,
        patient_id, project_id
    )
    .fetch_optional(db_pool)
    .await;

    debug!("id: {patient_id}, project id: {project_id}, data request: {:?}", data_request.is_err());
    data_request
}

pub async fn exists(db_pool: &SqlitePool, id: &str, project_id: &str) -> bool {
    let data_request = get_by(db_pool, id, project_id).await.unwrap_or(None);
    data_request.is_some()
}

pub async fn insert(db_pool: &SqlitePool, data_request: &DataRequest) -> Result<i64, sqlx::Error> {
    let insert_query_result = sqlx::query!(
        "INSERT INTO data_requests (id, patient_id, project_id, status) VALUES ($1, $2, $3, $4)",
        data_request.id,
        data_request.patient_id,
        data_request.project_id,
        data_request.status
    )
    .execute(db_pool)
    .await
    .map(|qr| qr.last_insert_rowid());

    insert_query_result
}
