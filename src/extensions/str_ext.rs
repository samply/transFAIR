pub trait StrExt {
    fn to_option_str(&self) -> Option<&str>;
}

impl StrExt for str {
    fn to_option_str(&self) -> Option<&str> {
        match self.trim() {
            "" => None,
            x => Some(x),
        }
    }
}
