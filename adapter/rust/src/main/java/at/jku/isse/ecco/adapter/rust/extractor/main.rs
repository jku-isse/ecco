#[cfg(feature = "v1")]
fn hello(str: &str){
    println!("Hello, {} from v1", str);
}

#[cfg(feature = "v2")]
fn farewell(str: &str){
    println!("Farewell, {} from v2", str);
}

#[cfg(all(not(feature = "foo"), any(feature = "v1", feature = "v2")))]
fn complex_condition() {
    println!("Not foot AND (v1 OR v2)");
}

fn main() {
    #[cfg(feature = "v1")]
    hello("world");

    #[cfg(feature = "v2")]
    farewell("world");

    println!("shared code");
}

