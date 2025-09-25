#[derive(Subcommand, Debug, Clone)]
#[command(rename_all = "lowercase")]
enum Commands {
    /// Change the password for an existing user
    Change {
        /// The username whose password will be changed
        #[arg(short, long)]
        user: String,
        /// The new password for the user
        #[arg(short, long)]
        password: String,
    },
    /// Get all users
    GetAll,
}