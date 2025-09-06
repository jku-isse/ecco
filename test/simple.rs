#[derive(Debug)]
struct Simple {
    value: i32,
}

trait DisplayValue {
    fn display(&self);
}

impl DisplayValue for Simple {
    fn display(&self) {
        println!("Value is: {}", self.value);
    }
}

fn main() {
    let s = Simple { value: 42 };
    s.display();
}