# GRANT

Navigation: [Up](Home.md) | [Home](../../Home.md)

Grants permissions to a principal.

## Syntax

```kafkasql
GRANT READ ON STREAM context.StreamName TO 'principal';
```

## Example

```kafkasql
GRANT READ ON STREAM customers.Customers TO 'analyst';
```
