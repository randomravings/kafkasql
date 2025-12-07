package kafkasql.linter;

import java.util.List;

import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.linter.rules.PascalCaseFieldsRule;
import kafkasql.linter.rules.PascalCaseTypesRule;
import kafkasql.linter.rules.ScreamingSnakeCaseEnumsRule;
import kafkasql.linter.rules.ExactCaseMemberReferenceRule;

/**
 * Orchestrates the execution of lint rules over an AST.
 * 
 * <p>The engine:
 * <ul>
 *   <li>Traverses the AST in depth-first order
 *   <li>Invokes each active rule for every node
 *   <li>Collects diagnostics from rules
 *   <li>Supports built-in and plugin rules
 * </ul>
 */
public final class LintEngine {
    
    private final List<LintRule> rules;
    
    /**
     * Creates a lint engine with built-in rules.
     */
    public LintEngine() {
        this(builtInRules());
    }
    
    /**
     * Creates a lint engine with specified rules.
     * 
     * @param rules list of lint rules to execute
     */
    public LintEngine(List<LintRule> rules) {
        this.rules = List.copyOf(rules);
    }
    
    /**
     * Returns the list of built-in lint rules.
     */
    public static List<LintRule> builtInRules() {
        return List.of(
            new PascalCaseTypesRule(),
            new PascalCaseFieldsRule(),
            new ScreamingSnakeCaseEnumsRule(),
            new ExactCaseMemberReferenceRule()
        );
    }
    
    /**
     * Lints a list of scripts and returns diagnostics.
     * 
     * @param scripts AST scripts to lint
     * @param symbols symbol table with type information
     * @param bindings binding environment with semantic info
     * @return diagnostics containing all lint issues
     */
    public Diagnostics lint(
        List<Script> scripts,
        SymbolTable symbols,
        BindingEnv bindings
    ) {
        Diagnostics diags = new Diagnostics();
        LintContextImpl ctx = new LintContextImpl(symbols, bindings, diags);
        
        for (Script script : scripts) {
            visitScript(script, ctx);
        }
        
        return diags;
    }
    
    private void visitScript(Script script, LintContextImpl ctx) {
        visitNode(script, ctx);
        for (Stmt stmt : script.statements()) {
            visitStmt(stmt, ctx);
        }
    }
    
    private void visitStmt(Stmt stmt, LintContextImpl ctx) {
        visitNode(stmt, ctx);
        
        switch (stmt) {
            case CreateStmt create -> visitCreate(create, ctx);
            default -> {}
        }
    }
    
    private void visitCreate(CreateStmt create, LintContextImpl ctx) {
        Decl decl = create.decl();
        visitNode(decl, ctx);
        
        switch (decl) {
            case TypeDecl typeDecl -> visitTypeDecl(typeDecl, ctx);
            case ContextDecl contextDecl -> {}
            case StreamDecl streamDecl -> {}
            default -> {}
        }
    }
    
    private void visitTypeDecl(TypeDecl typeDecl, LintContextImpl ctx) {
        TypeKindDecl kind = typeDecl.kind();
        visitNode(kind, ctx);
        
        switch (kind) {
            case ScalarDecl scalar -> {}
            case EnumDecl enumDecl -> visitEnum(enumDecl, ctx);
            case StructDecl struct -> visitStruct(struct, ctx);
            case UnionDecl union -> visitUnion(union, ctx);
            case DerivedTypeDecl derived -> {}
        }
    }
    
    private void visitEnum(EnumDecl enumDecl, LintContextImpl ctx) {
        for (EnumSymbolDecl symbol : enumDecl.symbols()) {
            visitNode(symbol, ctx);
        }
    }
    
    private void visitStruct(StructDecl struct, LintContextImpl ctx) {
        for (StructFieldDecl field : struct.fields()) {
            visitNode(field, ctx);
            // Visit fragments (DEFAULT, CHECK, etc.)
            for (DeclFragment fragment : field.fragments()) {
                if (fragment instanceof DefaultNode defaultNode) {
                    visitLiteral(defaultNode.value(), ctx);
                }
            }
        }
    }
    
    private void visitUnion(UnionDecl union, LintContextImpl ctx) {
        for (UnionMemberDecl member : union.members()) {
            visitNode(member, ctx);
        }
    }
    
    private void visitLiteral(LiteralNode literal, LintContextImpl ctx) {
        visitNode(literal, ctx);
        
        // Recursively visit nested literals
        switch (literal) {
            case StructLiteralNode struct -> {
                for (var entry : struct.fields()) {
                    visitLiteral(entry.value(), ctx);
                }
            }
            case ListLiteralNode list -> {
                for (LiteralNode elem : list.elements()) {
                    visitLiteral(elem, ctx);
                }
            }
            case UnionLiteralNode union -> {
                visitLiteral(union.value(), ctx);
            }
            default -> {}
        }
    }
    
    private void visitNode(AstNode node, LintContextImpl ctx) {
        for (LintRule rule : rules) {
            ctx.setCurrentRule(rule);
            rule.analyze(node, ctx);
        }
    }
    
    /**
     * Implementation of LintContext that collects diagnostics.
     */
    private static final class LintContextImpl implements LintContext {
        private final SymbolTable symbols;
        private final BindingEnv bindings;
        private final Diagnostics diagnostics;
        private LintRule currentRule;
        
        LintContextImpl(SymbolTable symbols, BindingEnv bindings, Diagnostics diagnostics) {
            this.symbols = symbols;
            this.bindings = bindings;
            this.diagnostics = diagnostics;
        }
        
        void setCurrentRule(LintRule rule) {
            this.currentRule = rule;
        }
        
        @Override
        public void report(Range range, String message) {
            report(range, currentRule.metadata().defaultSeverity(), message);
        }
        
        @Override
        public void report(Range range, DiagnosticEntry.Severity severity, String message) {
            String fullMessage = "[" + currentRule.metadata().qualifiedId() + "] " + message;
            
            switch (severity) {
                case INFO -> diagnostics.info(range, DiagnosticKind.LINT, DiagnosticCode.STYLE, fullMessage);
                case WARNING -> diagnostics.warning(range, DiagnosticKind.LINT, DiagnosticCode.STYLE, fullMessage);
                case ERROR -> diagnostics.error(range, DiagnosticKind.LINT, DiagnosticCode.STYLE, fullMessage);
                case FATAL -> diagnostics.fatal(range, DiagnosticKind.LINT, DiagnosticCode.STYLE, fullMessage);
            }
        }
        
        @Override
        public SymbolTable symbols() {
            return symbols;
        }
        
        @Override
        public BindingEnv bindings() {
            return bindings;
        }
    }
}
