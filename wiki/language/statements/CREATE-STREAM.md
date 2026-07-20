# CREATE STREAM

Navigation: [Up](Home.md) | [Home](../../Home.md)

Creates a stream schema.

## Syntax

```kafkasql
CREATE STREAM StreamName (
  TYPE RecordType AS STRUCT (...)
  DISTRIBUTE BY (KeyField)
);
```

## Example

```kafkasql
CREATE STREAM Customers (
  TYPE CustomerRecord AS STRUCT (
    Key INT64,
    Value customers.Customer
  )
  DISTRIBUTE BY (Key)
);
```

## Notes

- Key and value shapes are defined in the stream record type.
- Use qualified type names for cross-context references.
