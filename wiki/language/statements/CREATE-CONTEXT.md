# CREATE CONTEXT

Navigation: [Up](Home.md) | [Home](../../Home.md)

Declares a namespace context.

## Syntax

```kafkasql
CREATE CONTEXT context.name;
```

## Example

```kafkasql
CREATE CONTEXT customers;
CREATE CONTEXT com.example.orders;
```

## Notes

- Contexts define where names are declared.
- Nested contexts are supported with dot notation.
