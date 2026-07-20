# CREATE TYPE

Navigation: [Up](Home.md) | [Home](../../Home.md)

Creates a type declaration. KafkaSQL supports SCALAR, ENUM, STRUCT, and UNION.

## Syntax

```kafkasql
CREATE TYPE Name AS SCALAR INT32;
CREATE TYPE Name AS ENUM (...);
CREATE TYPE Name AS STRUCT (...);
CREATE TYPE Name AS UNION (...);
```

## Examples

```kafkasql
CREATE TYPE CustomerId AS SCALAR INT64;

CREATE TYPE CustomerStatus AS ENUM (
  ACTIVE = 0,
  INACTIVE = 1
);

CREATE TYPE Customer AS STRUCT (
  Id CustomerId,
  Name STRING,
  Status CustomerStatus
);

CREATE TYPE CustomerEvent AS UNION (
  Created customers.Customer,
  Deleted customers.CustomerId
);
```

## Notes

- Use fully qualified names when crossing contexts.
- Structs and scalars can include constraints and defaults.
