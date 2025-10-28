
pub fn from_bounds<I>(iter: &I) -> Option<usize>
where
    I: Iterator,
{
    helper(iter.size_hint())
}


fn helper(bounds: (usize, Option<usize>)) -> Option<usize> {
    match bounds {
        (lower, Some(upper)) if lower == upper => Some(upper),
        _ => None,
    }
}

