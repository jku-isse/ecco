use std::io::Write;

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


#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_create_user_and_get_password() {
        let user = "testuser";
        let password = "testpass";
        create_user(user, password).expect("Failed to create user");
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, Some(password.to_string()));
        // Clean up
        let _ = fs::remove_file(format!("{}.txt", user));
    }

    #[test]
    fn test_get_user_password_nonexistent() {
        let user = "nonexistentuser";
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, None);
    }
}

fn main() {}