#[cfg(feature="v2")]
fn farewell(str:&str){
    println!("Farewell, {} from v12", str);
}
#[cfg(feature="v1")]
fn hello(str:&str){
    println!("Hello, {} from v1", str);
}
fn main(){
    farewell("world");
    hello("world");
}
