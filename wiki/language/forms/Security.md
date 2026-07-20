# Security Forms

Navigation: [Up](../Home.md) | [Home](../../Home.md)

Security and access-control related statement forms.

## Statements

- [CREATE USER](../statements/CREATE-USER.md)
- [ALTER USER](../statements/ALTER-USER.md)
- [DROP USER](../statements/DROP-USER.md)
- [GRANT](../statements/GRANT.md)
- [REVOKE](../statements/REVOKE.md)

## Typical Flow

```kafkasql
CREATE USER analyst PASSWORD 'secret';
GRANT READ ON STREAM customers.Customers TO 'analyst';
REVOKE READ ON STREAM customers.Customers FROM 'analyst';
DROP USER analyst;
```
