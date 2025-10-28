macro_rules! crate_root {
    () => {
        /// A facade around all the types we need from the `std`, `core`, and `alloc`
        /// crates. This avoids elaborate import wrangling having to happen in every
        /// module.
        mod lib {
            mod core {
                #[cfg(not(feature = "std"))]
                pub use core::*;
            }

            pub use self::core::{f32, f64};
            pub use self::core::{iter, num, str};


            pub use self::core::cell::{Cell, RefCell};
            pub use self::core::cmp::Reverse;
            pub use self::core::fmt::{self, Debug, Display, Write as FmtWrite};
            pub use self::core::marker::PhantomData;
            pub use self::core::num::Wrapping;
            pub use self::core::ops::{Bound, Range, RangeFrom, RangeInclusive, RangeTo};
            pub use self::core::result;
            pub use self::core::time::Duration;








            #[cfg(all(not(no_core_cstr), not(feature = "std")))]
            pub use self::core::ffi::CStr;


            #[cfg(all(not(no_core_net), not(feature = "std")))]
            pub use self::core::net;





            #[cfg(not(no_core_num_saturating))]
            pub use self::core::num::Saturating;
        }

        // None of this crate's error handling needs the `From::from` error conversion
        // performed implicitly by the `?` operator or the standard library's `try!`
        // macro. This simplified macro gives a 5.5% improvement in compile time
        // compared to standard `try!`, and 9% improvement compared to `?`.
        macro_rules! tri {
            ($expr:expr) => {
                match $expr {
                    Ok(val) => val,
                    Err(err) => return Err(err),
                }
            };
        }

        #[cfg_attr(all(docsrs, if_docsrs_then_no_serde_core), path = "core/de/mod.rs")]
        pub mod de;
        #[cfg_attr(all(docsrs, if_docsrs_then_no_serde_core), path = "core/ser/mod.rs")]
        pub mod ser;

        #[cfg_attr(all(docsrs, if_docsrs_then_no_serde_core), path = "core/format.rs")]
        mod format;

        #[doc(inline)]
        pub use crate::de::{Deserialize, Deserializer};
        #[doc(inline)]
        pub use crate::ser::{Serialize, Serializer};

        // Used by generated code. Not public API.
        #[doc(hidden)]
        #[cfg_attr(
            all(docsrs, if_docsrs_then_no_serde_core),
            path = "core/private/mod.rs"
        )]
        mod private;

        // Used by declarative macro generated code. Not public API.
        #[doc(hidden)]
        pub mod __private {
            #[doc(hidden)]
            pub use crate::private::doc;
            #[doc(hidden)]
            pub use core::result::Result;
        }

        include!(concat!(env!("OUT_DIR"), "/private.rs"));

    };
}

