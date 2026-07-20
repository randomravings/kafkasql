# ALTER CURSOR

Navigation: [Up](Home.md) | [Home](../../Home.md)

Updates stream assignments and positions of an existing cursor.

## Syntax

Add or remove streams:

```kafkasql
ALTER CURSOR 'cursor-name' ADD STREAM context.StreamName RESET EARLIEST|LATEST;
ALTER CURSOR 'cursor-name' REMOVE STREAM context.StreamName;
```

Reset stream position:

```kafkasql
ALTER CURSOR 'cursor-name' RESET STREAM context.StreamName TO BEGINNING|END;
```

Seek by partition target:

```kafkasql
ALTER CURSOR 'cursor-name' SEEK STREAM context.StreamName TO (
  0: BEGINNING,
  1: END,
  2: 1001,
  3: '2026-07-01T00:00:00.001Z'
);
```

## Example

```kafkasql
ALTER CURSOR 'customers-live' ADD STREAM customers.CustomerReplay RESET EARLIEST;
ALTER CURSOR 'customers-live' RESET STREAM customers.Customers TO BEGINNING;
```

## Notes

- RESET STREAM operates at stream level.
- SEEK STREAM operates at partition level.
