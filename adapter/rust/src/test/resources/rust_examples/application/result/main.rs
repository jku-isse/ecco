use clap::{Parser, Subcommand};
use std::io::Write;
#[derive(Parser)]
#[command(author, version, about)]
struct Args {
    #[command(subcommand)]
    cmd: Commands,
}
#[derive(Subcommand, Debug, Clone)]
#[command(rename_all = "lowercase")]
enum Commands {
    /// Create a new user with a password
    Create {
        /// The username for the new user
        #[arg(short, long)]
        user: String,
        /// The password for the user
        #[arg(short, long)]
        password: String,
    },
    /// Get all users
    GetAll,
    /// Retrieve the password for a user
    Get {
        /// The username whose password will be retrieved
        #[arg(short, long)]
        user: String,
    },
}
/// Creates a new user file and writes the provided password to it.
///
/// # Arguments
///
/// * `user` - The username for which the file will be created.
/// Creates a new user file and writes the provided password to it.
/// * `password` - The password to store in the user's file
fn create_user(user: &str, password: &str) ->Result<(),std::io::Error> {
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
/// Retrieves all users sorted
/// * `Option<String>` - The password if the file exists, or `None` if not found.
fn get_all_users() ->Vec<String> {
    let mut users = Vec::new();
    let entries = match std::fs::read_dir(".") {
        Ok(entries) => entries,
        Err(_) => return users, // Return empty vec if directory can't be read
    };
    for entry_result in entries {
        let entry = match entry_result {
            Ok(entry) => entry,
            Err(_) => continue, // Skip entries with errors
        };
        let path = entry.path();
        // Skip if not a .txt file
        if path.extension().map_or(true, |ext| ext != "txt") {
            continue;
        }
        // Get filename without extension
        if let Some(user) = path.file_stem().and_then(|stem| stem.to_str()) {
            users.push(user.to_string());
        }
    }
    users.sort();
    users
}
fn get_user_password(user: &str) ->Option<String> {
    let file_path = format!("{}.txt", user);
    if std::path::Path::new(&file_path).exists() {
        let content = std::fs::read_to_string(file_path).expect("Unable to read file");
        return Some(content);
    }
    None
}
fn run_app(args: Args) {
    match args.cmd {
        Commands::Create { user, password } => {
            if let Err(e) = create_user(&user, &password) {
                println!("Error creating user: {}", e);
            } else {
                println!("User {} created successfully.", user);
            }
        }
        Commands::GetAll => {
            let users = get_all_users();
            if users.is_empty() {
                println!("No users found.");
            } else {
                println!("Users:");
                for user in users {
                    println!("- {}", user);
                }
            }
        }
        Commands::Get { user } => match get_user_password(&user) {
            Some(password) => println!("Password for {}: {}", user, password),
            _ => println!("No password found for user: {}", user),
        },
    }
}
fn main() {
    let args = Args::parse();
    run_app(args);
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
fn test_get_all_users() {
        let users = ["user1", "user2", "user3"];
        let password = "testpass";
        // Create test users
        for user in &users {
            let _ = create_user(user, password);
        }
        // Get all users
        let found_users = get_all_users();
        // Verify all test users are found (note: this might find other .txt files too)
        for user in &users {
            assert!(found_users.contains(&user.to_string()));
        }
        // Clean up after test
        for user in &users {
            let _ = fs::remove_file(format!("{}.txt", user));
        }
}
fn test_get_user_password_nonexistent() {
        let user = "nonexistentuser";
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, None);
}
}
