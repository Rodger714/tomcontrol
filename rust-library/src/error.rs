#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Wrong adapter count")]
    WrongAdapterCount,
    #[error("No such characteristic")]
    NoSuchCharacteristic,
}