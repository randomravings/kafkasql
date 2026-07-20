# Cursor Forms

Navigation: [Up](../Home.md) | [Home](../../Home.md)

Cursor-related statement forms.

## Statements

- [CREATE CURSOR](../statements/CREATE-CURSOR.md)
- [ALTER CURSOR](../statements/ALTER-CURSOR.md)
- [DROP CURSOR](../statements/DROP-CURSOR.md)

## Typical Flow

```kafkasql
CREATE CURSOR 'customers-live' FOR STREAMS (
  customers.Customers RESET EARLIEST
);

ALTER CURSOR 'customers-live' RESET STREAM customers.Customers TO BEGINNING;

DROP CURSOR 'customers-live';
```
