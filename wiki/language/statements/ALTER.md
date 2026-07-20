# ALTER

Navigation: [Up](Home.md) | [Home](../../Home.md)

Top-level ALTER statement family.

## Forms

- [ALTER CURSOR](ALTER-CURSOR.md)
- [ALTER USER](ALTER-USER.md)

## Quick Examples

```kafkasql
ALTER CURSOR 'customers-live' RESET STREAM customers.Customers TO BEGINNING;
ALTER USER analyst PASSWORD 'new-secret';
```

## Notes

- `ALTER` mutates existing objects.
- Supported forms may expand as the language evolves.
