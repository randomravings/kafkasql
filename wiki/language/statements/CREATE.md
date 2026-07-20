# CREATE

Navigation: [Up](Home.md) | [Home](../../Home.md)

Top-level CREATE statement family.

## Forms

- [CREATE CONTEXT](CREATE-CONTEXT.md)
- [CREATE TYPE](CREATE-TYPE.md)
- [CREATE STREAM](CREATE-STREAM.md)
- [CREATE CURSOR](CREATE-CURSOR.md)
- [CREATE USER](CREATE-USER.md)

## Quick Examples

```kafkasql
CREATE CONTEXT customers;
CREATE TYPE CustomerId AS SCALAR INT64;
CREATE STREAM Customers (
  TYPE CustomerRecord AS STRUCT (Key INT64, Value customers.Customer)
  DISTRIBUTE BY (Key)
);
CREATE CURSOR 'customers-live' FOR STREAMS (
  customers.Customers RESET EARLIEST
);
CREATE USER analyst PASSWORD 'secret';
```

## Notes

- `CREATE` declares new named objects.
- Object type is determined by the second keyword (`CONTEXT`, `TYPE`, `STREAM`, `CURSOR`, `USER`).
