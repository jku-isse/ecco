mod seed;

// FIXME: #[cfg(doctest)] once https://github.com/rust-lang/rust/issues/67295 is fixed.
#[doc(hidden)]
pub mod doc;

#[doc(hidden)]
pub mod size_hint;

#[doc(hidden)]
pub mod string;

#[doc(hidden)]
pub use self::seed::InPlaceSeed;
#[doc(hidden)]
pub use crate::lib::result::Result;

