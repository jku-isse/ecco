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