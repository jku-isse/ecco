struct Point {
    x: i32,
    y: i32,
}
struct Wrapper<T>
where
    T: Clone,
{
    value: T,
}
struct Marker;
struct Marker;
struct Color(u8, u8, u8);
struct Pair<T, U>(T, U)
where
    T: Copy,
    U: Clone;
#[derive(Debug)]
pub struct Visible(pub i32, #[cfg(test)] i32);
#[cfg(feature = "v3")]
#[derive(Debug)]
pub struct Data {
    pub id: u32,
    #[cfg(feature = "extra")]
    extra: Option<String>,
}
