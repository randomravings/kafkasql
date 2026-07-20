# READ

Navigation: [Up](Home.md) | [Home](../../Home.md)

Reads records from a stream.

## Syntax

```kafkasql
READ FROM context.StreamName
  TYPE RecordType *;
```

With cursor:

```kafkasql
READ FROM context.StreamName
  FROM CURSOR 'context.cursorName'
  TYPE RecordType *;
```

## Example

```kafkasql
READ FROM customers.Customers
  FROM CURSOR 'customers.customers-live'
  TYPE CustomerRecord *;
```

## Notes

- Cursor references in READ must be fully qualified.
- Format is 'context.cursorName'.
