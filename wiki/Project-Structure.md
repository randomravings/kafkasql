# Project Structure

KafkaSQL is organized as a multi-module Gradle project with clear separation of concerns.

## Module Overview

```bash
kafkasql/
├── runtime/          # Core types, diagnostics, and evaluation
├── lang/            # Lexer, parser, and semantic analysis
├── linter/          # Pluggable lint rules
├── pipeline/        # Compilation orchestration
├── cli/             # Command-line interface
├── lsp/             # Language Server Protocol implementation
├── codegen/         # Code generation (future)
├── io/              # I/O utilities (future)
├── engine/          # Runtime execution engine (future)
└── vscode-extension/ # VS Code extension (TypeScript)
```

## Module Dependencies

The architecture follows a strict layering with no circular dependencies:

```bash
runtime (base layer - diagnostics, types, values)
   ↓
lang (parsing, semantic analysis)
   ↓
linter (lint rules)
   ↓
pipeline (orchestration)
   ↓
cli, lsp (consumers)
```

## Core Modules

### :runtime

**Purpose:** Foundation layer providing cross-cutting infrastructure

**Key Responsibilities:**

- Error reporting with severity, ranges, and categories
- Type system (ScalarType, EnumType, StructType, UnionType, StreamType)
- Runtime value representations
- Fully qualified name handling

**Dependencies:** None (base module)

### :lang

**Purpose:** Language processing - lexing, parsing, and semantic analysis

**Key Responsibilities:**

- ANTLR-based lexer and parser
- AST representation (declarations, literals, expressions)
- Semantic analysis and type binding
- Symbol table for global name resolution
- AST node → semantic value mapping
- Input abstractions (file and string inputs)
- Include file dependency resolution

**Dependencies:** :runtime

**Key Features:**

- Case-insensitive parsing
- Context-aware type resolution
- Include file dependency ordering with cycle detection
- Precise error location tracking

### :linter

**Purpose:** Extensible linting framework with pluggable rules

**Key Responsibilities:**

- Lint rule interface and execution engine
- Context API for rules (symbol access, bindings, reporting)
- ServiceLoader-based rule discovery
- Built-in rules for naming conventions (PascalCase types/fields, SCREAMING_SNAKE_CASE enums, exact case union members)

**Dependencies:** :runtime, :lang

**Extensibility:** New rules auto-discovered via ServiceLoader

### :pipeline

**Purpose:** Unified compilation orchestration

**Key Responsibilities:**

- Stateless pipeline with builder pattern
- Phase interface for compilation stages
- Immutable configuration context
- Mutable state model passed through phases
- Built-in phases: ParsePhase, SemanticPhase, LintPhase

**Dependencies:** :runtime, :lang, :linter

**Design Principles:**

- Stateless pipeline, reusable across invocations
- Mutable model carries state through phases
- Early exit on fatal errors
- Extensible phase system

### :cli

**Purpose:** Command-line compiler interface

**Key Responsibilities:**

- Argument parsing and validation
- Pipeline execution orchestration
- Shadow JAR packaging (all-in-one executable)

**Features:**

- Interactive REPL mode
- File compilation with include resolution
- Lint-only mode, AST printing, verbose output
- Configurable working directory

**Dependencies:** :pipeline (transitively includes all)

### :lsp

**Purpose:** Language Server Protocol implementation for IDE integration

**Key Responsibilities:**

- LSP server implementation
- Document synchronization and change tracking
- Real-time diagnostic publishing
- Shadow JAR packaging with LSP4J

**Features:**

- Real-time diagnostics on file changes
- Full error reporting (syntax, semantic, lint)
- Severity mapping for IDE UI
- Document lifecycle event handling

**Dependencies:** :pipeline, LSP4J library

### :vscode-extension

**Purpose:** VS Code extension for KafkaSQL language support

**Technology:** TypeScript, VS Code Extension API

**Key Responsibilities:**

- Extension activation and lifecycle management
- LSP client initialization and communication
- TextMate grammar for syntax highlighting
- Language configuration (comments, brackets, auto-closing)

**Features:**

- Syntax highlighting for `.kafka` files
- Real-time error/warning squiggles
- Integrated diagnostics panel
- Language Server connection management

## Build System

**Gradle 9.0** with configuration caching

**Key Tasks:**

- `./gradlew build` - Build all modules
- `./gradlew :cli:shadowJar` - Build CLI executable JAR
- `./gradlew :lsp:shadowJar` - Build LSP server JAR
- `./gradlew test` - Run all tests

## Testing

Each module includes comprehensive tests:

- **:lang** - Parser tests, semantic tests, include resolution tests
- **:linter** - Rule validation tests
- **:pipeline** - Integration tests

Test framework: JUnit 5

## Recent Architectural Changes

### Pipeline Refactoring (December 2025)

Migrated from manual phase orchestration to unified pipeline:

- **Before:** CLI and LSP manually orchestrated parse → bind → lint
- **After:** Single `Pipeline.execute()` call with pluggable phases
- **Benefit:** Consistent execution, easier testing, extensible architecture

### Diagnostics Module Movement

Moved diagnostics from `:lang` to `:runtime`:

- **Reason:** Diagnostics are cross-cutting infrastructure, not language-specific
- **Benefit:** Reusable for future languages, cleaner module boundaries

### Include Error Reporting Enhancement

Added precise error locations for INCLUDE failures:

- **Before:** Errors reported at `[-1:-1--1:-1]` (no location)
- **After:** Errors point to exact INCLUDE statement with range
- **Implementation:** `IncludeWithRange` pairs Path with Range through resolution

### Union Member Case Linting

Fixed type name resolution in `ExactCaseMemberReferenceRule`:

- **Problem:** Unqualified names in AST vs FQN in symbol table
- **Solution:** Hybrid lookup - try UnionValue binding, fallback to searching all types
- **Benefit:** Lint rule works correctly in both CLI and LSP
