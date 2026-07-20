# Type Forms

Navigation: [Up](../Home.md) | [Home](../../Home.md)

Type-related statement forms.

## Statements

- [CREATE TYPE](../statements/CREATE-TYPE.md)

## Typical Flow

```kafkasql
CREATE TYPE CustomerId AS SCALAR INT64;
CREATE TYPE Customer AS STRUCT (
  Id CustomerId,
  Name STRING
);
```
