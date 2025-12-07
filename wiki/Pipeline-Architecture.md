# Pipeline Architecture

The KafkaSQL compiler uses a **pipeline architecture** for organizing compilation phases. This design provides flexibility, testability, and extensibility.

## Overview

The pipeline orchestrates compilation in distinct phases:

1. **ParsePhase** - Lexing, parsing, and include resolution
2. **SemanticPhase** - Type binding and validation
3. **LintPhase** - Code style checking
4. *(Future)* CodeGenPhase, OptimizationPhase, etc.

## Core Components

### Pipeline

The `Pipeline` class is the main orchestrator.

**Design Principles:**

- **Stateless** - A single pipeline instance can be reused across multiple compilations
- **Immutable context** - Configuration is fixed at construction time
- **Mutable model** - State flows through phases via `PipelineModel`
- **Early exit** - Stops at first fatal error

**Example Usage:**

```java
Pipeline pipeline = Pipeline.builder()
    .context(pipelineContext)
    .addPhase(new ParsePhase())
    .addPhase(new SemanticPhase())
    .addPhase(new LintPhase())
    .build();

PipelineResult result = pipeline.execute();
```

### PipelineContext

Immutable configuration passed to all phases.

**Fields:**

- `inputs` - List of Input objects to compile
- `workingDir` - Base directory for include resolution
- `includeResolution` - Whether to resolve INCLUDE statements
- `verbose` - Enable detailed output

**Creation:**

```java
PipelineContext ctx = PipelineContext.builder()
    .addInput(new FileInput("schema.kafka", path))
    .workingDir(Paths.get("."))
    .includeResolution(true)
    .verbose(false)
    .build();
```

### PipelineModel

Mutable state container passed through phases.

**Fields:**

- `inputs` - Ordered list of inputs (after include resolution)
- `parseResults` - Map of input → ParseResult
- `bindingEnv` - Semantic bindings (AST node → type/value)
- `symbolTable` - Global symbol table (name → declaration)

**Lifecycle:**

1. **Created empty** by Pipeline
2. **Populated by ParsePhase** - adds inputs and parse results
3. **Enhanced by SemanticPhase** - adds bindings and symbols
4. **Read by LintPhase** - accesses all accumulated state

### Phase

Interface for compilation phases.

```java
public interface Phase {
    void execute(PipelineContext ctx, PipelineModel model, Diagnostics diags);
}
```

**Contract:**

- Read from `ctx` (immutable config)
- Read/write to `model` (accumulated state)
- Report errors to `diags`
- Stop early if `diags.hasError()` and phase requires clean input

## Built-in Phases

### ParsePhase

**Responsibility:** Lexing, parsing, and include dependency resolution

**Inputs:**

- `ctx.inputs` - Initial file/string inputs
- `ctx.workingDir` - Base path for includes
- `ctx.includeResolution` - Enable/disable include processing

**Outputs:**

- `model.inputs` - Resolved and ordered inputs (after includes)
- `model.parseResults` - Map of input source → ParseResult

**Process:**

1. If `includeResolution` enabled:
   - Call `IncludeResolver.buildIncludeOrder()`
   - Resolves INCLUDE statements recursively
   - Detects cycles
   - Returns topologically sorted list
2. For each input:
   - Tokenize with ANTLR lexer
   - Parse with ANTLR parser
   - Collect diagnostics
3. Store results in model

**Key Feature:** Include resolution happens BEFORE parsing, so parser sees a flat list of files in dependency order.

### SemanticPhase

**Responsibility:** Type binding and semantic validation

**Inputs:**

- `model.parseResults` - Parsed AST trees
- `ctx` - Context for error reporting

**Outputs:**

- `model.bindingEnv` - AST node → semantic value mappings
- `model.symbolTable` - Global name → declaration mappings

**Process:**

1. For each ParseResult:
   - Visit AST with `SemanticBinder`
   - Resolve type references
   - Build symbol table
   - Bind expressions to types
   - Validate constraints
2. Accumulate all bindings and symbols

**Validation:**

- Unknown type references → error
- Duplicate declarations → error
- Type mismatches → error
- Invalid CHECK expressions → error

### LintPhase

**Responsibility:** Code style and convention checking

**Inputs:**

- `model.parseResults` - AST trees
- `model.bindingEnv` - Semantic bindings
- `model.symbolTable` - Symbol table

**Outputs:**

- Lint diagnostics (warnings) added to `diags`

**Process:**

1. Load lint rules via ServiceLoader
2. For each rule:
   - Visit all AST nodes
   - Check style violations
   - Report warnings

**Lint Rules:**

- `PascalCaseTypesRule` - Type names → PascalCase
- `PascalCaseFieldsRule` - Field names → PascalCase
- `ScreamingSnakeCaseEnumsRule` - Enum symbols → SCREAMING_SNAKE_CASE
- `ExactCaseMemberReferenceRule` - Union member references match exact case

**Key Feature:** Linting is non-fatal. Warnings are reported but don't stop compilation.

## Execution Flow

```bash
Pipeline.execute()
  │
  ├─> Create empty PipelineModel
  │
  ├─> For each Phase:
  │     ├─> phase.execute(ctx, model, diags)
  │     └─> if (diags.hasError()) break;
  │
  └─> Return PipelineResult(model, diags)
```

### Error Handling

**Early Exit:**

- If any phase produces fatal errors, subsequent phases are skipped
- Allows user to fix fundamental errors before seeing secondary issues

**Diagnostic Categories:**

- PARSER - Syntax errors
- SEMANTIC - Type errors, unknown references
- LINT - Style warnings
- TYPE - Type system violations
- INTERNAL - Compiler bugs

**Severity Levels:**

- FATAL - Stops compilation immediately
- ERROR - Stops after current phase
- WARNING - Continues compilation
- INFO - Informational only

## Extensibility

### Adding a New Phase

1. **Implement Phase interface:**

```java
public class MyCustomPhase implements Phase {
    @Override
    public void execute(PipelineContext ctx, PipelineModel model, Diagnostics diags) {
        // Access model state
        var parseResults = model.parseResults();
        var symbols = model.symbolTable();
        
        // Do work
        // ...
        
        // Report diagnostics
        diags.error(range, kind, code, "message");
    }
}
```

**2. Add to pipeline:**

```java
Pipeline pipeline = Pipeline.builder()
    .context(ctx)
    .addPhase(new ParsePhase())
    .addPhase(new SemanticPhase())
    .addPhase(new LintPhase())
    .addPhase(new MyCustomPhase())  // ← Add here
    .build();
```

### Adding a New Lint Rule

**1. Implement LintRule:**

```java
@LintMetadata(
    id = "my-custom-rule",
    category = "Naming",
    description = "Enforces custom naming convention"
)
public class MyCustomRule implements LintRule {
    @Override
    public void analyze(AstNode node, LintContext ctx) {
        if (node instanceof TypeDecl typeDecl) {
            String name = typeDecl.name().name();
            if (!name.startsWith("T")) {
                ctx.report(
                    typeDecl.name().range(),
                    "Type names should start with 'T'"
                );
            }
        }
    }
}
```

**2. Register via ServiceLoader:**

Create `META-INF/services/kafkasql.linter.LintRule`:

```bash
com.example.MyCustomRule
```

**3. Rule is automatically discovered** - No code changes needed!

## Integration

### CLI Integration

```java
Pipeline pipeline = Pipeline.builder()
    .context(pipelineContext)
    .addPhase(new ParsePhase())
    .addPhase(new SemanticPhase())
    .addPhase(new LintPhase())
    .build();

PipelineResult result = pipeline.execute();

if (result.diagnostics().hasError()) {
    System.exit(1);
}
```

### LSP Integration

```java
// Create pipeline once
Pipeline pipeline = Pipeline.builder()
    .context(...)
    .addPhase(new ParsePhase())
    .addPhase(new SemanticPhase())
    .addPhase(new LintPhase())
    .build();

// Reuse on every document change
void onDocumentChange(String uri, String content) {
    PipelineContext ctx = createContext(uri, content);
    PipelineResult result = pipeline.execute(ctx);
    
    sendDiagnostics(uri, result.diagnostics());
}
```

**Benefits:**

- Single pipeline instance serves entire LSP session
- Fast incremental compilation
- Consistent behavior with CLI

## Design Rationale

### Why Stateless Pipeline?

**Allows reuse:**

- LSP can create pipeline once, use for all edits
- No need to rebuild phase objects

**Thread-safe:**

- Multiple threads can share same pipeline
- State is in PipelineModel, not Pipeline

### Why Mutable Model?

**Performance:**

- Avoid copying large data structures between phases
- Phases append to model incrementally

**Flexibility:**

- Each phase adds what it needs
- Later phases can access earlier results

### Why Separate Context and Model?

**Clarity:**

- `Context` = what user configured (immutable)
- `Model` = what compiler computed (mutable)

**Testability:**

- Easy to mock contexts for testing
- Models can be inspected after execution

## Future Phases

### CodeGenPhase

Generate code for target languages (Java, Rust, etc.)

**Inputs:** model.symbolTable, model.bindingEnv
**Outputs:** Generated source files

### OptimizationPhase

Optimize schemas for serialization

**Inputs:** model.symbolTable
**Outputs:** Optimized type layouts

### ValidationPhase

Runtime validation code generation

**Inputs:** CHECK constraints from model
**Outputs:** Validation functions

### DocumentationPhase

Generate API documentation

**Inputs:** Type declarations and comments
**Outputs:** Markdown/HTML docs
