# KafkaSQL Command Reference

Navigation: [Up](Home.md) | [Home](../Home.md)

A simplified, PostgreSQL-style command index for KafkaSQL.

Use this page to quickly find a command, then open its dedicated statement page.

## Command Index

| Command | Purpose | Reference |
| ------- | ------- | --------- |
| INCLUDE | Import definitions from another file | [INCLUDE](statements/INCLUDE.md) |
| CREATE | Create objects such as contexts, types, streams, cursors, users | [CREATE](statements/CREATE.md) |
| USE | Change active context | [USE](statements/USE.md) |
| ALTER | Modify existing objects | [ALTER](statements/ALTER.md) |
| DROP | Remove existing objects | [DROP](statements/DROP.md) |
| READ | Read records from streams | [READ](statements/READ.md) |
| WRITE | Write records to streams | [WRITE](statements/WRITE.md) |
| SHOW | List metadata objects | [SHOW](statements/SHOW.md) |
| EXPLAIN | Show statement execution plan | [EXPLAIN](statements/EXPLAIN.md) |
| GRANT | Grant permissions | [GRANT](statements/GRANT.md) |
| REVOKE | Revoke permissions | [REVOKE](statements/REVOKE.md) |

## Common Forms By Object

### Contexts

- [Context Forms](forms/Contexts.md)
- [CREATE CONTEXT](statements/CREATE-CONTEXT.md)
- [USE CONTEXT](statements/USE-CONTEXT.md)

### Types

- [Type Forms](forms/Types.md)
- [CREATE TYPE](statements/CREATE-TYPE.md)

### Streams

- [Stream Forms](forms/Streams.md)
- [CREATE STREAM](statements/CREATE-STREAM.md)
- [READ](statements/READ.md)
- [WRITE](statements/WRITE.md)

### Cursors

- [Cursor Forms](forms/Cursors.md)
- [CREATE CURSOR](statements/CREATE-CURSOR.md)
- [ALTER CURSOR](statements/ALTER-CURSOR.md)
- [DROP CURSOR](statements/DROP-CURSOR.md)

### Security

- [Security Forms](forms/Security.md)
- [CREATE USER](statements/CREATE-USER.md)
- [ALTER USER](statements/ALTER-USER.md)
- [DROP USER](statements/DROP-USER.md)
- [GRANT](statements/GRANT.md)
- [REVOKE](statements/REVOKE.md)

## Quick Notes

- Keywords are case-insensitive.
- Names are context-based.
- Cursors are context-scoped and cannot be created globally.
- READ cursor references must be fully qualified as 'context.cursorName'.
