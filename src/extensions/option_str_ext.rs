/// Extension methods for working with Option<&str>.
pub trait OptionStrExt {
    fn to_option_string(&self) -> Option<String>;
}

impl OptionStrExt for Option<&str> {    
    /// Converts Option<&str> to Option<String>
    fn to_option_string(&self) -> Option<String> {
        self.map(str::to_string)
    }
}
