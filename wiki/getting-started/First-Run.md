# First Run

Navigation: [Up](Home.md) | [Home](../Home.md)

Use these steps to get productive quickly.

## 1. Build Core Modules

```bash
./gradlew :lang:compileJava :lsp:compileJava
```

## 2. Build CLI Fat Jar

```bash
./gradlew :cli:shadowJar
```

## 3. Build VS Code Extension

```bash
cd vscode-extension
npm install
npm run build
```

## 4. Try an Example Schema

```bash
./kafkasql -f examples/com/example.kafka
```

## Next Steps

- Learn statement syntax in [Syntax Quick Reference](../language/Syntax-Reference.md)
- Learn deeper language rules in [Language Syntax](../language/Language-Syntax.md)
- Learn compiler design in [Pipeline Architecture](../architecture/Pipeline-Architecture.md)
