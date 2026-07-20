# WRITE

Navigation: [Up](Home.md) | [Home](../../Home.md)

Writes records to a stream.

## Syntax

```kafkasql
WRITE TO context.StreamName
TYPE RecordType
VALUES (...);
```

## Example

```kafkasql
WRITE TO customers.Customers
TYPE CustomerRecord
VALUES (
  { Key: 1, Value: { Id: 1, Name: 'Ada', Status: customers.CustomerStatus::ACTIVE } }
);
```

## Notes

- The written payload must match the declared record shape.
- Type mismatches are reported by semantic checks.
