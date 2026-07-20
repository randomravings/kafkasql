# USE CONTEXT

Navigation: [Up](Home.md) | [Home](../../Home.md)

Switches the active declaration context.

## Syntax

```kafkasql
USE CONTEXT context.name;
```

## Example

```kafkasql
CREATE CONTEXT customers;
USE CONTEXT customers;

CREATE TYPE CustomerId AS SCALAR INT64;
```

## Notes

- Unqualified names resolve from the active context.
- You can still use fully qualified names at any time.
