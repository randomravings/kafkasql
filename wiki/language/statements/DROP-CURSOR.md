# DROP CURSOR

Navigation: [Up](Home.md) | [Home](../../Home.md)

Deletes a cursor.

## Syntax

```kafkasql
DROP CURSOR 'cursor-name';
```

## Example

```kafkasql
DROP CURSOR 'customers-live';
```

## Notes

- Removing a cursor removes its stored read positions.
- Future reads must use another cursor.
