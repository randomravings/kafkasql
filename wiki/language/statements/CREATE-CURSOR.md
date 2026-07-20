# CREATE CURSOR

Navigation: [Up](Home.md) | [Home](../../Home.md)

Creates a named cursor over one or more streams.

## Syntax

```kafkasql
CREATE CURSOR 'cursor-name' FOR STREAMS (
  context.StreamA RESET EARLIEST,
  context.StreamB RESET LATEST
);
```

## Example

```kafkasql
CREATE CURSOR 'customers-live' FOR STREAMS (
  customers.Customers RESET EARLIEST,
  customers.CustomerAudit RESET LATEST
);
```

## Notes

- Cursors are scoped to a context.
- Cursors are not global.
