# DROP

Navigation: [Up](Home.md) | [Home](../../Home.md)

Top-level DROP statement family.

## Forms

- [DROP CURSOR](DROP-CURSOR.md)
- [DROP USER](DROP-USER.md)

## Quick Examples

```kafkasql
DROP CURSOR 'customers-live';
DROP USER analyst;
```

## Notes

- `DROP` removes objects from the catalog.
- Removal semantics depend on object type.
