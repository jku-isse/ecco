//! A doc comment that applies to the implicit anonymous module of this crate
pub mod outer_module {
    //!  - Inner line doc
    //!! - Still an inner line doc (but with a bang at the beginning)
    /*!  - Inner block doc */
    /*!! - Still an inner block doc (but with a bang at the beginning) */
    //   - Only a comment
    ///  - Outer line doc (exactly 3 slashes)
    //// - Only a comment
    /*   - Only a comment */
    /**  - Outer block doc (exactly) 2 asterisks */
    /*** - Only a comment */
    pub mod inner_module {
    }
    pub mod nested_comments {
        /* In Rust /* we can /* nest comments */ */ */
        // All three types of block comments can contain or be nested inside
        // any other type:
        /*   /* */  /** */  /*! */  */
        /*!  /* */  /** */  /*! */  */
        /**  /* */  /** */  /*! */  */
        pub mod dummy_item {
        }
    }
}
