package kafkasql.io;

public enum ReadResultCode {
    // read was successful, value is present.
    SUCCESS,
    // read was successful, but type was excluded by user selection.
    SKIPPED,
    // read was successful, but no handling was defined for this type.
    ADDITIONAL
}
