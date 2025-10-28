use crate::lib::*;

#[cfg(any(feature = "std", feature = "alloc"))]
#[doc(hidden)]
pub fn from_utf8_lossy(bytes: &[u8]) -> Cow<'_, str> {
    String::from_utf8_lossy(bytes)
}

// The generated code calls this like:
//
//     let value = &_serde::__private::from_utf8_lossy(bytes);
//     Err(_serde::de::Error::unknown_variant(value, VARIANTS))
//
// so it is okay for the return type to be different from the std case as long
// as the above works.

