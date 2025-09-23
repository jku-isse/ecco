#[cfg(feature = "v2")]
fn farewell(str: &str){
    println!("Farewell, {} from v12", str);
}

fn main(){
    farewell("world");
}
