# StreamSQL (Java + Groovy)

Complete package with:

- ANTLR grammar (`SqlStream.g4`)
- Java AST (`streamsql.ast`) + AST builder visitor
- Semantic validator (catalog + basic checks)
- Java `Main` and Groovy `console.groovy`
- `examples/demo.sqls`

## Build

```bash
./gradlew generateGrammarSource build
```

## Run (Java)

```bash
./gradlew run
```

## Run (Groovy scripting)

First build (so compiled classes are on disk):

```bash
./gradlew generateGrammarSource build
groovy -cp build/classes/java/main:build/resources/main:build/generated-src/antlr/main src/main/groovy/console.groovy examples/demo.sqls
```
