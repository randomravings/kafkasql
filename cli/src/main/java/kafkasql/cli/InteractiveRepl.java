package kafkasql.cli;

import kafkasql.runtime.value.StructValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive REPL (Read-Eval-Print-Loop) for KafkaSQL.
 * Maintains engine state across statements within a session.
 */
public class InteractiveRepl {
    
    private final InteractiveEngine engine;
    private final BufferedReader reader;
    private final List<String> sessionStatements; // Accumulate statements for context
    private StringBuilder multilineBuffer;
    private boolean inMultilineMode;
    private String currentContext = "(global)"; // Track current context for prompt
    
    public InteractiveRepl() {
        this.engine = new InteractiveEngine();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.sessionStatements = new ArrayList<>();
        this.multilineBuffer = new StringBuilder();
        this.inMultilineMode = false;
    }
    
    public void run() throws IOException {
        printWelcome();
        
        String line;
        while (true) {
            String prompt = inMultilineMode ? "... " : currentContext + ">> ";
            System.out.print(prompt);
            System.out.flush();
            
            line = reader.readLine();
            if (line == null) {
                // EOF (Ctrl+D)
                break;
            }
            
            line = line.trim();
            
            // Handle special commands
            if (!inMultilineMode && handleCommand(line)) {
                continue;
            }
            
            // Handle multi-line mode
            if (line.endsWith("\\")) {
                inMultilineMode = true;
                multilineBuffer.append(line, 0, line.length() - 1).append(" ");
                continue;
            }
            
            // Execute statement
            String statement;
            if (inMultilineMode) {
                multilineBuffer.append(line);
                statement = multilineBuffer.toString();
                multilineBuffer = new StringBuilder();
                inMultilineMode = false;
            } else {
                statement = line;
            }
            
            if (!statement.isBlank()) {
                executeStatement(statement);
            }
        }
        
        System.out.println("\nGoodbye!");
    }
    
    private void printWelcome() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  KafkaSQL Interactive Shell");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Commands:");
        System.out.println("    .help     - Show this help");
        System.out.println("    .clear    - Clear all data and reset state");
        System.out.println("    .streams  - List all streams");
        System.out.println("    .exit     - Exit the shell");
        System.out.println();
        System.out.println("  Use \\ at end of line for multi-line statements");
        System.out.println("  Press Ctrl+D to exit");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
    }
    
    private boolean handleCommand(String line) {
        if (!line.startsWith(".")) {
            return false;
        }
        
        String cmd = line.toLowerCase();
        
        switch (cmd) {
            case ".help", ".h" -> {
                printWelcome();
                return true;
            }
            case ".exit", ".quit", ".q" -> {
                System.out.println("Goodbye!");
                System.exit(0);
            }
            case ".clear" -> {
                engine.clear();
                sessionStatements.clear();
                System.out.println("✓ All data cleared");
                return true;
            }
            case ".streams" -> {
                // Get declared streams from symbol table
                var model = engine.getLastModel();
                if (model == null || model.symbols()._decl.isEmpty()) {
                    System.out.println("No streams declared yet");
                } else {
                    var streams = engine.getAllStreams(); // Data map
                    var declaredStreams = model.symbols()._decl.entrySet().stream()
                        .filter(e -> e.getValue() instanceof kafkasql.lang.syntax.ast.decl.StreamDecl)
                        .toList();
                    
                    if (declaredStreams.isEmpty()) {
                        System.out.println("No streams declared yet");
                    } else {
                        System.out.println("Streams:");
                        for (var entry : declaredStreams) {
                            var name = entry.getKey();
                            var records = streams.getOrDefault(name, java.util.List.of());
                            System.out.println("  " + name.fullName() + " (" + records.size() + " records)");
                        }
                    }
                }
                return true;
            }
            default -> {
                System.out.println("Unknown command: " + line);
                System.out.println("Type .help for available commands");
                return true;
            }
        }
        
        return false;
    }
    
    private void executeStatement(String statement) {
        try {
            // Execute with all previous successful statements plus the new one
            // Don't add to history yet - only add if execution succeeds
            List<String> statementsToExecute = new ArrayList<>(sessionStatements);
            statementsToExecute.add(statement);
            String[] allStatements = statementsToExecute.toArray(new String[0]);
            engine.executeAll(allStatements);
            
            // Only add to session history if execution succeeded
            sessionStatements.add(statement);
            
            // Update current context if this was a USE CONTEXT statement
            updateCurrentContext(statement);
            
            // Check if there are query results to display
            List<StructValue> results = engine.getLastQueryResult();
            
            if (!results.isEmpty()) {
                System.out.println();
                System.out.println("Results (" + results.size() + " record" + (results.size() == 1 ? "" : "s") + "):");
                System.out.println("─────────────────────────────────────────────────────────");
                
                for (int i = 0; i < results.size(); i++) {
                    StructValue record = results.get(i);
                    System.out.println("[" + i + "] " + formatRecord(record));
                }
                System.out.println();
            } else {
                // Provide more descriptive success messages
                String msg = getSuccessMessage(statement);
                System.out.println(msg);
            }
            
        } catch (RuntimeException e) {
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.err.println();
        }
    }
    
    /**
     * Update the current context display based on the statement executed.
     */
    private void updateCurrentContext(String statement) {
        String trimmed = statement.trim();
        String upper = trimmed.toUpperCase();
        
        if (upper.startsWith("USE CONTEXT")) {
            // Extract context name from "USE CONTEXT x.y.z;" - handle dotted names
            String afterUse = trimmed.substring("USE CONTEXT".length()).trim();
            String contextName = afterUse.replace(";", "").trim();
            if (!contextName.isEmpty()) {
                currentContext = "[" + contextName + "]";
            }
        } else if (upper.startsWith("CREATE CONTEXT")) {
            // Extract context name from "CREATE CONTEXT x;" - no auto-switch
            // Just for generating the success message
        }
    }
    
    /**
     * Generate descriptive success message based on statement type.
     */
    private String getSuccessMessage(String statement) {
        String trimmed = statement.trim();
        String upper = trimmed.toUpperCase();
        
        if (upper.startsWith("CREATE CONTEXT")) {
            String afterCreate = trimmed.substring("CREATE CONTEXT".length()).trim();
            String name = afterCreate.replace(";", "").trim();
            if (!name.isEmpty()) {
                // Build fully qualified name based on current context
                String fqn = name;
                if (!currentContext.equals("(global)")) {
                    // Extract context name from "[contextName]" format
                    String ctx = currentContext.substring(1, currentContext.length() - 1);
                    fqn = ctx + "." + name;
                }
                return "✓ Context '" + fqn + "' created. Use 'USE CONTEXT " + fqn + ";' to switch to it.";
            }
            return "✓ Context created";
        } else if (upper.startsWith("USE CONTEXT")) {
            String afterUse = trimmed.substring("USE CONTEXT".length()).trim();
            String name = afterUse.replace(";", "").trim();
            if (!name.isEmpty()) {
                return "✓ Now using context '" + name + "'";
            }
            return "✓ Context switched";
        } else if (upper.startsWith("CREATE STREAM")) {
            return "✓ Stream created successfully";
        } else if (upper.startsWith("CREATE TYPE")) {
            return "✓ Type created successfully";
        } else if (upper.startsWith("WRITE")) {
            return "✓ Records written successfully";
        } else {
            return "✓ OK";
        }
    }
    
    private String formatRecord(StructValue record) {
        var fields = record.fields();
        if (fields.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append(": ");
            sb.append(formatValue(entry.getValue()));
        }
        sb.append(" }");
        return sb.toString();
    }
    
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "'" + value + "'";
        } else if (value instanceof StructValue struct) {
            return formatRecord(struct);
        } else if (value instanceof List<?> list) {
            return "[" + list.stream()
                .map(this::formatValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("") + "]";
        } else {
            return value.toString();
        }
    }
}
