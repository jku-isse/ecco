
#[macro_use]
mod crate_root;
#[macro_use]
mod macros;

crate_root!();

#[macro_export]
#[doc(hidden)]
macro_rules! __require_serde_not_serde_core {
    () => {
        ::core::compile_error!(
            "Serde derive requires a dependency on the serde crate, not serde_core"
        );
    };
}

