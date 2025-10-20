#[cfg(feature = "v1")]
fn hello(str: &str){
    println!("Hello, {} from v1", str);
}

fn main(){
    #[cfg(feature = "v1")]
    hello("world");
}
