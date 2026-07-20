# Stream Forms

Navigation: [Up](../Home.md) | [Home](../../Home.md)

Stream-related statement forms.

## Statements

- [CREATE STREAM](../statements/CREATE-STREAM.md)
- [READ](../statements/READ.md)
- [WRITE](../statements/WRITE.md)

## Typical Flow

```kafkasql
CREATE STREAM Customers (
  TYPE CustomerRecord AS STRUCT (Key INT64, Value customers.Customer)
  DISTRIBUTE BY (Key)
);

WRITE TO customers.Customers TYPE CustomerRecord VALUES (...);
READ FROM customers.Customers TYPE CustomerRecord *;
```
