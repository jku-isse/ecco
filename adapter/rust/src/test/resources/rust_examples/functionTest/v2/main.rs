#[cfg(feature = "v2")]
fn farewell(str: &str){
    println!("Farewell, {} from v2", str);
}

fn main(){
    #[cfg(feature = "v2")]
    farewell("world");
}
