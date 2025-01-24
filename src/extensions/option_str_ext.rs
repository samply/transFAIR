pub trait OptionStrExt {
    fn to_option_string(&self) -> Option<String>;
}

impl OptionStrExt for Option<&str> {    
    fn to_option_string(&self) -> Option<String> {
        self.map(str::to_string)
    }
}
