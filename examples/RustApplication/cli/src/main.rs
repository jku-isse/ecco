use clap::{Parser, Subcommand};
use std::io::Write;

#[derive(Parser)]
#[command(author, version, about)]
struct Args {
    #[command(subcommand)]
    cmd: Commands,
}

#[cfg(feature = "hello")]
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

fn run_app(args: Args) {
    match args.cmd {
        Commands::Create { user, password } => {
            if let Err(e) = create_user(&user, &password) {
                eprintln!("Error creating user: {}", e);
            } else {
                println!("User {} created successfully.", user);
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
    fn test_get_user_password_nonexistent() {
        let user = "nonexistentuser";
        let retrieved = get_user_password(user);
        assert_eq!(retrieved, None);
    }
}
