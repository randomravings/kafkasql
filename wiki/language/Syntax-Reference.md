# KafkaSQL Syntax Reference

Navigation: [Up](Home.md) | [Home](../Home.md)

This page is the summary hub for statement docs.

## Statement Index

- [SQL Commands](SQL-Commands.md)
- [Statement Home](statements/Home.md)

### Core Statements

- [INCLUDE](statements/INCLUDE.md)
- [CREATE](statements/CREATE.md)
- [USE](statements/USE.md)
- [ALTER](statements/ALTER.md)
- [DROP](statements/DROP.md)
- [READ](statements/READ.md)
- [WRITE](statements/WRITE.md)

### Discovery and Planning

- [SHOW](statements/SHOW.md)
- [EXPLAIN](statements/EXPLAIN.md)

### Security Statements

- [GRANT](statements/GRANT.md)
- [REVOKE](statements/REVOKE.md)

### Forms By Object

#### Contexts

- [Context Forms](forms/Contexts.md)
- [CREATE CONTEXT](statements/CREATE-CONTEXT.md)
- [USE CONTEXT](statements/USE-CONTEXT.md)

#### Types

- [Type Forms](forms/Types.md)
- [CREATE TYPE](statements/CREATE-TYPE.md)

#### Streams

- [Stream Forms](forms/Streams.md)
- [CREATE STREAM](statements/CREATE-STREAM.md)
- [READ](statements/READ.md)
- [WRITE](statements/WRITE.md)

#### Cursors

- [Cursor Forms](forms/Cursors.md)
- [CREATE CURSOR](statements/CREATE-CURSOR.md)
- [ALTER CURSOR](statements/ALTER-CURSOR.md)
- [DROP CURSOR](statements/DROP-CURSOR.md)

#### Security

- [Security Forms](forms/Security.md)
- [CREATE USER](statements/CREATE-USER.md)
- [ALTER USER](statements/ALTER-USER.md)
- [DROP USER](statements/DROP-USER.md)
- [GRANT](statements/GRANT.md)
- [REVOKE](statements/REVOKE.md)

## Quick Rules

- Keywords are case-insensitive.
- Names are context-based.
- Cursors are context-scoped and cannot be created globally.
- READ cursor references must be fully qualified, using 'context.cursorName'.

## Notes

- For a broader language deep dive, see [Language Syntax](Language-Syntax.md).
