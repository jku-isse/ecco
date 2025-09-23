#[cfg(feature = "v1")]
fn hello(str: &str){
    println!("Hello, {} from v1", str);
}

fn main(){
    hello("world");
}
