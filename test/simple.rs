#[cfg(target_os = "linux")]
pub fn test(string: &str) -> String {
    println!("Hello from Rust! {}", string);
}