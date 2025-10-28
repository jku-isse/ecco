
// Rustdoc has a lot of shortcomings related to cross-crate re-exports that make
// the rendered documentation of serde_core traits in serde more challenging to
// understand than the equivalent documentation of the same items in serde_core.
// https://github.com/rust-lang/rust/labels/A-cross-crate-reexports
// So, just for the purpose of docs.rs documentation, we inline the contents of
// serde_core into serde. This sidesteps all the cross-crate rustdoc bugs.


#[cfg(not(docsrs))]
macro_rules! crate_root {
    () => {
        /// A facade around all the types we need from the `std`, `core`, and `alloc`
        /// crates. This avoids elaborate import wrangling having to happen in every
        /// module.
        mod lib {
            mod core {
                #[cfg(feature = "std")]
                pub use std::*;
            }

            pub use self::core::{f32, f64};
            pub use self::core::{ptr, str};

            #[cfg(any(feature = "std", feature = "alloc"))]
            pub use self::core::slice;

            pub use self::core::clone;
            pub use self::core::convert;
            pub use self::core::default;
            pub use self::core::fmt::{self, Debug, Display, Write as FmtWrite};
            pub use self::core::marker::{self, PhantomData};
            pub use self::core::option;
            pub use self::core::result;

            #[cfg(feature = "std")]
            pub use std::borrow::{Cow, ToOwned};

            #[cfg(feature = "std")]
            pub use std::string::{String, ToString};

            #[cfg(feature = "std")]
            pub use std::vec::Vec;

            #[cfg(feature = "std")]
            pub use std::boxed::Box;
        }

        // None of this crate's error handling needs the `From::from` error conversion
        // performed implicitly by the `?` operator or the standard library's `try!`
        // macro. This simplified macro gives a 5.5% improvement in compile time
        // compared to standard `try!`, and 9% improvement compared to `?`.
        #[cfg(not(no_serde_derive))]
        macro_rules! tri {
            ($expr:expr) => {
                match $expr {
                    Ok(val) => val,
                    Err(err) => return Err(err),
                }
            };
        }

        ////////////////////////////////////////////////////////////////////////////////

        pub use serde_core::{
            de, forward_to_deserialize_any, ser, Deserialize, Deserializer, Serialize, Serializer,
        };

        // Used by generated code and doc tests. Not public API.
        #[doc(hidden)]
        mod private;

        include!(concat!(env!("OUT_DIR"), "/private.rs"));
    };
}

crate_root!();

mod integer128;

// Re-export #[derive(Serialize, Deserialize)].
//
// The reason re-exporting is not enabled by default is that disabling it would
// be annoying for crates that provide handwritten impls or data formats. They
// would need to disable default features and then explicitly re-enable std.
#[cfg(feature = "serde_derive")]
extern crate serde_derive;

/// Derive macro available if serde is built with `features = ["derive"]`.
#[cfg(feature = "serde_derive")]
#[cfg_attr(docsrs, doc(cfg(feature = "derive")))]
pub use serde_derive::{Deserialize, Serialize};

#[macro_export]
#[doc(hidden)]
macro_rules! __require_serde_not_serde_core {
    () => {};
}

