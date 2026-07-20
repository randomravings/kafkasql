# INCLUDE

Navigation: [Up](Home.md) | [Home](../../Home.md)

Imports definitions from another KafkaSQL file.

## Syntax

```kafkasql
INCLUDE 'relative/path/to/file.kafka';
```

## Example

```kafkasql
INCLUDE 'types/customers/Customer.kafka';
```

## Notes

- Paths are resolved relative to the configured working directory.
- Include dependency cycles are rejected.
