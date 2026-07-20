# EXPLAIN

Navigation: [Up](Home.md) | [Home](../../Home.md)

Shows the execution plan for a statement.

## Syntax

```kafkasql
EXPLAIN <statement>;
```

## Example

```kafkasql
EXPLAIN READ FROM customers.Customers TYPE CustomerRecord *;
```

## Notes

- Use EXPLAIN to understand how a statement will be executed.
- Useful before running reads in production scenarios.
