use axum::{
    Json, Router,
    extract::Path,
    http::StatusCode,
    routing::{get, post},
};
use serde::{Deserialize, Serialize};
use std::io::Write;

/// Request body for creating a user.
#[derive(Deserialize)]
struct CreateUserRequest {
    user: String,
    password: String,
}

#[derive(Serialize)]
struct GetUserResponse {
    user: String,
    password: String,
}

/// Creates a new user file and writes the provided password to it.
///
/// # Arguments
///
/// * `user` - The username for which the file will be created.
/// * `password` - The password to store in the user's file.
fn create_user(user: &str, password: &str) -> Result<(), std::io::Error> {
    println!("Creating user: {} with password: {}", user, password);

    let mut file = std::fs::File::create(format!("{}.txt", user))?;
    file.write_all(password.as_bytes())?;
    Ok(())
}

/// Retrieves the password for a given user from their file, if it exists.
///
/// # Arguments
///
/// * `user` - The username whose password will be retrieved.
///
/// # Returns
///
/// * `Option<String>` - The password if the file exists, or `None` if not found.
fn get_user_password(user: &str) -> Option<String> {
    let file_path = format!("{}.txt", user);
    if std::path::Path::new(&file_path).exists() {
        let content = std::fs::read_to_string(file_path).expect("Unable to read file");
        return Some(content);
    }
    None
}

async fn create_user_handler(
    Json(payload): Json<CreateUserRequest>,
) -> Result<StatusCode, (StatusCode, String)> {
    create_user(&payload.user, &payload.password)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(StatusCode::CREATED)
}

async fn get_user_handler(Path(user): Path<String>) -> Result<Json<GetUserResponse>, StatusCode> {
    match get_user_password(&user) {
        Some(password) => Ok(Json(GetUserResponse { user, password })),
        None => Err(StatusCode::NOT_FOUND),
    }
}

#[tokio::main]
async fn main() {
    let app = Router::new()
        .route("/users", post(create_user_handler))
        .route("/users/:user", get(get_user_handler));

    println!("Listening on http://0.0.0.0:3000");
    axum::Server::bind(&"0.0.0.0:3000".parse().unwrap())
        .serve(app.into_make_service())
        .await
        .unwrap();
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_create_user_and_get_password() {
        let user = "testuser";
        let password = "testpassword";
        // Clean up before test
        let _ = fs::remove_file(format!("{}.txt", user));
        // Create user
        let result = create_user(user, password);
        assert!(result.is_ok());
        // Retrieve password
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, Some(password.to_string()));
        // Clean up after test
        let _ = fs::remove_file(format!("{}.txt", user));
    }

    #[test]
    fn test_get_user_password_nonexistent() {
        let user = "nonexistentuser";
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, None);
    }
}
