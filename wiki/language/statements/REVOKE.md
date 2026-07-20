# REVOKE

Navigation: [Up](Home.md) | [Home](../../Home.md)

Revokes permissions from a principal.

## Syntax

```kafkasql
REVOKE READ ON STREAM context.StreamName FROM 'principal';
```

## Example

```kafkasql
REVOKE READ ON STREAM customers.Customers FROM 'analyst';
```
