package kafkasql.io;

/**
 * Enum representing the result of a read operation.
 */
public enum ReadResult {
    /**
     * The record is accpected by the query.
     */
    ACCEPTED,
    /**
     * The record was excluded based on the TYPE selection.
     */
    TYPE_EXLCUDED,
    /**
     * The record was filtered out based on the WHERE clause.
     */
    FILTERED_OUT,
    /**
     * The write schema was updated.
     */
    SCHEMA
}
