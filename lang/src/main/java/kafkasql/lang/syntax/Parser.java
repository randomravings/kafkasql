package kafkasql.lang.syntax;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import groovyjarjarantlr4.v4.runtime.Token;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.diagnostics.Pos;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.lex.SqlStreamLexer;
import kafkasql.lang.parse.SqlStreamParser;
import kafkasql.lang.parse.SqlStreamParserBaseVisitor;
import kafkasql.lang.syntax.ast.*;
import kafkasql.lang.syntax.ast.constExpr.*;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.*;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.misc.*;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.type.*;
import kafkasql.lang.syntax.ast.use.*;

/**
 * AstBuilder
 *
 * Builds the kafkasql.lang.ast.* model from SqlStreamParser parse trees.
 */
public final class Parser extends SqlStreamParserBaseVisitor<AstNode> {

    public Parser() { }

    public static Script buildTree(ParserArgs args) {
        ParserErrorListener errs = new ParserErrorListener(
            args.source(),
            args.diags()
        );
        SqlStreamParser parser = new SqlStreamParser(
            args.tokens()
        );
        parser.removeErrorListeners();
        parser.addErrorListener(errs);
        parser.setTrace(args.trace());

        SqlStreamParser.ScriptContext script = parser.script();
        AstBuilder builder = new AstBuilder(args.source(), args.diags());
        return builder.visitScript(script);
    }

    public static List<Include> scanTopIncludes(ParserArgs args) {
        List<Include> incs = new java.util.ArrayList<>();
        ParserErrorListener errs = new ParserErrorListener(
            args.source(),
            args.diags()
        );
        SqlStreamParser parser = new SqlStreamParser(
            args.tokens()
        );
        parser.removeErrorListeners();
        parser.addErrorListener(errs);
        parser.setTrace(args.trace());

        SqlStreamParser.ScriptContext script = parser.script();
        AstBuilder builder = new AstBuilder(args.source(), args.diags());
        if (script.includeSection() != null) {
            for (SqlStreamParser.IncludePragmaContext ic : script.includeSection().includePragma()) {
                Include include = builder.visitIncludePragma(ic);
                incs.add(include);
            }
        }
        return incs;
    }

    private static class AstBuilder extends SqlStreamParserBaseVisitor<AstNode> {
        private final String _source;
        private final Diagnostics _diags;
        
        public AstBuilder(String source, Diagnostics diags) {
            this._source = source;
            this._diags = diags;
        }

        // ========================================================================
        // Top-level: script / statements
        // ========================================================================

        @Override
        public Script visitScript(SqlStreamParser.ScriptContext ctx) {
            AstListNode<Include> includes = visitIncludeSection(ctx.includeSection());
            AstListNode<Stmt> stmts = visitStatementList(ctx.statementList());
            return new Script(range(ctx), includes, stmts);
        }

        @Override
        public AstListNode<Include> visitIncludeSection(SqlStreamParser.IncludeSectionContext ctx) {
            AstListNode<Include> includes = new AstListNode<>(Include.class);
            if (ctx == null)
                return includes;
            for (SqlStreamParser.IncludePragmaContext ic : ctx.includePragma())
                includes.add(visitIncludePragma(ic));
            return includes;
        }

        @Override
        public Include visitIncludePragma(SqlStreamParser.IncludePragmaContext ctx) {
            Range range = range(ctx);
            String path = unquote(ctx.STRING_LIT().getText());
            return new Include(range, path);
        }

        @Override
        public AstListNode<Stmt> visitStatementList(SqlStreamParser.StatementListContext ctx) {
            AstListNode<Stmt> stmts = new AstListNode<>(Stmt.class);
            if (ctx == null)
                return stmts;
            for (SqlStreamParser.StatementContext sc : ctx.statement())
                stmts.add(visitStatement(sc));
            return stmts;
        }

        @Override
        public Stmt visitStatement(SqlStreamParser.StatementContext ctx) {
            if (ctx.useStmt() != null)
                return visitUseStmt(ctx.useStmt());
            if (ctx.readStmt() != null)
                return visitReadStmt(ctx.readStmt());
            if (ctx.writeStmt() != null)
                return visitWriteStmt(ctx.writeStmt());
            if (ctx.createStmt() != null)
                return visitCreateStmt(ctx.createStmt());
            
            // Syntax error - report and return placeholder
            Range range = range(ctx);
            reportSyntaxError(range, "Expected USE, READ, WRITE, or CREATE statement");
            Identifier errorId = new Identifier(range, "<error>");
            QName errorQName = QName.of(errorId);
            return new UseStmt(range, new ContextUse(range, errorQName));
        }

        // ========================================================================
        // Documentation comments
        // ========================================================================

        @Override
        public DocNode visitCommentFragment(SqlStreamParser.CommentFragmentContext ctx) {
            Range range = range(ctx);
            String raw = ctx.STRING_LIT().getText();
            int closingIndent = ctx.COMMENT().getSymbol().getCharPositionInLine();
            String inner = extractTripleQuotedInner(raw);
            String norm = normalizeDoc(inner, closingIndent);
            return new DocNode(range, norm);
        }

        @Override
        public DefaultNode visitDefaultFragment(SqlStreamParser.DefaultFragmentContext ctx) {
            Range range = range(ctx);
            LiteralNode value = visitLiteral(ctx.literal());
            return new DefaultNode(range, value);
        }

        // ========================================================================
        // USE
        // ========================================================================

        @Override
        public UseStmt visitUseStmt(SqlStreamParser.UseStmtContext ctx) {
            Range range = range(ctx);
            ContextUse contextUse = visitContextUse(ctx.contextUse());
            return new UseStmt(range, contextUse);
        }

        @Override
        public ContextUse visitContextUse(SqlStreamParser.ContextUseContext ctx) {
            return new ContextUse(range(ctx), visitQname(ctx.qname()));
        }

        // ========================================================================
        // CREATE
        // ========================================================================

        @Override
        public CreateStmt visitCreateStmt(SqlStreamParser.CreateStmtContext ctx) {
            Range range = range(ctx);
            Decl decl = visitDecl(ctx.decl());
            return new CreateStmt(range, decl);
        }

        @Override
        public Decl visitDecl(SqlStreamParser.DeclContext ctx) {
            if (ctx.contextDecl() != null)
                return visitContextDecl(ctx.contextDecl());
            if (ctx.typeDecl() != null)
                return visitTypeDecl(ctx.typeDecl());
            if (ctx.streamDecl() != null)
                return visitStreamDecl(ctx.streamDecl());
            
            // Syntax error - report and return placeholder
            Range range = range(ctx);
            reportSyntaxError(range, "Expected CONTEXT, TYPE, or STREAM declaration");
            Identifier errorName = new Identifier(range, "<error>");
            return new ContextDecl(range, errorName, new AstListNode<>(DeclFragment.class));
        }

        @Override
        public ContextDecl visitContextDecl(SqlStreamParser.ContextDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitContextName(ctx.contextName());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            return new ContextDecl(range, name, fragments);
        }

        @Override
        public Identifier visitContextName(SqlStreamParser.ContextNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public TypeDecl visitTypeDecl(SqlStreamParser.TypeDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitTypeName(ctx.typeName());
            
            // Check if typeKindDecl is null (syntax error case)
            if (ctx.typeKindDecl() == null) {
                reportSyntaxError(range, 
                    "Expected type definition after TYPE keyword. " +
                    "Use: TYPE <name> AS STRUCT (...) or TYPE <name> AS SCALAR <primitive> etc.");
                // Return a placeholder to continue parsing
                Identifier errorId = new Identifier(range, "<error>");
                QName errorQName = QName.of(errorId);
                ComplexTypeNode errorType = new ComplexTypeNode(range, errorQName);
                TypeKindDecl errorKind = new DerivedTypeDecl(range, errorType);
                return new TypeDecl(range, name, errorKind, new AstListNode<>(DeclFragment.class));
            }
            
            TypeKindDecl typeKindDecl = visitTypeKindDecl(ctx.typeKindDecl());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            return new TypeDecl(range, name, typeKindDecl, fragments);
        }

        @Override
        public Identifier visitTypeName(SqlStreamParser.TypeNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public TypeKindDecl visitTypeKindDecl(SqlStreamParser.TypeKindDeclContext ctx) {
            if (ctx.scalarDecl() != null)
                return visitScalarDecl(ctx.scalarDecl());
            if (ctx.enumDecl() != null)
                return visitEnumDecl(ctx.enumDecl());
            if (ctx.structDecl() != null)
                return visitStructDecl(ctx.structDecl());
            if (ctx.unionDecl() != null)
                return visitUnionDecl(ctx.unionDecl());
            if (ctx.derivedType() != null)
                return visitDerivedType(ctx.derivedType());
            
            // Syntax error - report and return placeholder
            Range range = range(ctx);
            reportSyntaxError(range, "Expected SCALAR, ENUM, STRUCT, UNION, or type reference");
            Identifier errorId = new Identifier(range, "<error>");
            QName errorQName = QName.of(errorId);
            ComplexTypeNode errorType = new ComplexTypeNode(range, errorQName);
            return new DerivedTypeDecl(range, errorType);
        }

        // ========================================================================
        // QNAME / IDENTIFIER
        // ========================================================================

        @Override
        public QName visitQname(SqlStreamParser.QnameContext ctx) {
            Range range = range(ctx);
            AstListNode<Identifier> parts = new AstListNode<>(Identifier.class);
            for (SqlStreamParser.IdentifierContext ic : ctx.identifier())
                parts.add(visitIdentifier(ic));
            return new QName(range, parts);
        }

        @Override
        public Identifier visitIdentifier(SqlStreamParser.IdentifierContext ctx) {
            if (ctx.ID() == null) {
                Range range = range(ctx);
                _diags.syntaxError(
                    range,
                    "Expected identifier but found invalid token. Check for typos in type names (use INT32, STRING, etc.)"
                );
                return new Identifier(range, "<error>");
            }
            return new Identifier(range(ctx), ctx.ID().getText());
        }

        @Override
        public AstNode visitDotPrefix(SqlStreamParser.DotPrefixContext ctx) {
            // Dot prefix is not modeled in AST (QName.parts already holds the parts).
            return null;
        }

        // ========================================================================
        // DECLARATION TAIL FRAGMENTS
        // ========================================================================

        @Override
        public AstListNode<DeclFragment> visitDeclTailFragments(SqlStreamParser.DeclTailFragmentsContext ctx) {
            AstListNode<DeclFragment> fragments = new AstListNode<>(DeclFragment.class);
            if (ctx == null)
                return fragments;
            for (SqlStreamParser.DeclTailFragmentContext dtfc : ctx.declTailFragment())
                fragments.add(visitDeclTailFragment(dtfc));
            return fragments;
        }

        @Override
        public DeclFragment visitDeclTailFragment(SqlStreamParser.DeclTailFragmentContext ctx) {
            if (ctx.commentFragment() != null)
                return visitCommentFragment(ctx.commentFragment());
            if (ctx.constraintFragment() != null)
                return visitConstraintFragment(ctx.constraintFragment());
            if (ctx.namedConstraintFragment() != null)
                return visitNamedConstraintFragment(ctx.namedConstraintFragment());
            if (ctx.distributeFragment() != null)
                return visitDistributeFragment(ctx.distributeFragment());
            if (ctx.timestampFragment() != null)
                return visitTimestampFragment(ctx.timestampFragment());
            
            // Syntax error - report and return placeholder
            Range range = range(ctx);
            reportSyntaxError(range, "Invalid declaration fragment");
            return new DocNode(range, "");
        }

        @Override
        public DeclFragment visitConstraintFragment(SqlStreamParser.ConstraintFragmentContext ctx) {
            if (ctx.defaultFragment() != null)
                return visitDefaultFragment(ctx.defaultFragment());
            if (ctx.checkFragment() != null)
                return visitCheckFragment(ctx.checkFragment());
            
            // Syntax error - report and return placeholder
            Range range = range(ctx);
            reportSyntaxError(range, "Expected DEFAULT or CHECK constraint");
            return new DocNode(range, "");
        }

        @Override
        public ConstraintNode visitNamedConstraintFragment(SqlStreamParser.NamedConstraintFragmentContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            DeclFragment fragment = visitConstraintFragment(ctx.constraintFragment());
            return new ConstraintNode(range, name, fragment);
        }

        // ========================================================================
        // TYPES
        // ========================================================================

        @Override
        public TypeNode visitType(SqlStreamParser.TypeContext ctx) {
            if (ctx.primitiveType() != null)
                return visitPrimitiveType(ctx.primitiveType());
            if (ctx.compositeType() != null)
                return visitCompositeType(ctx.compositeType());
            if (ctx.typeReference() != null)
                return visitTypeReference(ctx.typeReference());
            
            // Syntax error - report and return placeholder type reference
            Range range = range(ctx);
            reportSyntaxError(range, "Expected primitive, composite, or named type");
            Identifier errorId = new Identifier(range, "<error>");
            return new ComplexTypeNode(range, QName.of(errorId));
        }

        @Override
        public PrimitiveTypeNode visitPrimitiveType(SqlStreamParser.PrimitiveTypeContext ctx) {
            if (ctx.booleanType() != null)
                return visitBooleanType(ctx.booleanType());
            if (ctx.int8Type() != null)
                return visitInt8Type(ctx.int8Type());
            if (ctx.int16Type() != null)
                return visitInt16Type(ctx.int16Type());
            if (ctx.int32Type() != null)
                return visitInt32Type(ctx.int32Type());
            if (ctx.int64Type() != null)
                return visitInt64Type(ctx.int64Type());
            if (ctx.float32Type() != null)
                return visitFloat32Type(ctx.float32Type());
            if (ctx.float64Type() != null)
                return visitFloat64Type(ctx.float64Type());
            if (ctx.decimalType() != null)
                return visitDecimalType(ctx.decimalType());
            if (ctx.stringType() != null)
                return visitStringType(ctx.stringType());
            if (ctx.bytesType() != null)
                return visitBytesType(ctx.bytesType());
            if (ctx.uuidType() != null)
                return visitUuidType(ctx.uuidType());
            if (ctx.dateType() != null)
                return visitDateType(ctx.dateType());
            if (ctx.timeType() != null)
                return visitTimeType(ctx.timeType());
            if (ctx.timestampType() != null)
                return visitTimestampType(ctx.timestampType());
            if (ctx.timestampTzType() != null)
                return visitTimestampTzType(ctx.timestampTzType());
            
            // Syntax error - report and return placeholder string type
            Range range = range(ctx);
            reportSyntaxError(range, "Unknown primitive type");
            return PrimitiveTypeNode.string(range, 0L);
        }

        @Override
        public PrimitiveTypeNode visitBooleanType(SqlStreamParser.BooleanTypeContext ctx) {
            return PrimitiveTypeNode.bool(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitInt8Type(SqlStreamParser.Int8TypeContext ctx) {
            return PrimitiveTypeNode.int8(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitInt16Type(SqlStreamParser.Int16TypeContext ctx) {
            return PrimitiveTypeNode.int16(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitInt32Type(SqlStreamParser.Int32TypeContext ctx) {
            return PrimitiveTypeNode.int32(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitInt64Type(SqlStreamParser.Int64TypeContext ctx) {
            return PrimitiveTypeNode.int64(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitFloat32Type(SqlStreamParser.Float32TypeContext ctx) {
            return PrimitiveTypeNode.float32(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitFloat64Type(SqlStreamParser.Float64TypeContext ctx) {
            return PrimitiveTypeNode.float64(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitDecimalType(SqlStreamParser.DecimalTypeContext ctx) {
            Range range = range(ctx);
            long precision = Long.parseLong(ctx.NUMBER_LIT(0).getText());
            long scale = Long.parseLong(ctx.NUMBER_LIT(1).getText());
            return PrimitiveTypeNode.decimal(range, precision, scale);
        }

        @Override
        public PrimitiveTypeNode visitStringType(SqlStreamParser.StringTypeContext ctx) {
            Range range = range(ctx);
            if (ctx.NUMBER_LIT() != null) {
                long len = Long.parseLong(ctx.NUMBER_LIT().getText());
                return PrimitiveTypeNode.string(range, len);
            }
            return PrimitiveTypeNode.string(range);
        }

        @Override
        public PrimitiveTypeNode visitBytesType(SqlStreamParser.BytesTypeContext ctx) {
            Range range = range(ctx);
            if (ctx.NUMBER_LIT() != null) {
                long len = Long.parseLong(ctx.NUMBER_LIT().getText());
                return PrimitiveTypeNode.bytes(range, len);
            }
            return PrimitiveTypeNode.bytes(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitUuidType(SqlStreamParser.UuidTypeContext ctx) {
            return PrimitiveTypeNode.uuid(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitDateType(SqlStreamParser.DateTypeContext ctx) {
            return PrimitiveTypeNode.date(range(ctx));
        }

        @Override
        public PrimitiveTypeNode visitTimeType(SqlStreamParser.TimeTypeContext ctx) {
            Range range = range(ctx);
            long precision = 0L;
            if (ctx.NUMBER_LIT() != null)
                precision = Long.parseLong(ctx.NUMBER_LIT().getText());
            return PrimitiveTypeNode.time(range, precision);
        }

        @Override
        public PrimitiveTypeNode visitTimestampType(SqlStreamParser.TimestampTypeContext ctx) {
            Range range = range(ctx);
            long precision = 0L;
            if (ctx.NUMBER_LIT() != null)
                precision = Long.parseLong(ctx.NUMBER_LIT().getText());
            return PrimitiveTypeNode.timestamp(range, precision);
        }

        @Override
        public PrimitiveTypeNode visitTimestampTzType(SqlStreamParser.TimestampTzTypeContext ctx) {
            Range range = range(ctx);
            long precision = 0L;
            if (ctx.NUMBER_LIT() != null)
                precision = Long.parseLong(ctx.NUMBER_LIT().getText());
            return PrimitiveTypeNode.timestampTz(range, precision);
        }

        @Override
        public CompositeTypeNode visitCompositeType(SqlStreamParser.CompositeTypeContext ctx) {
            if (ctx.listType() != null)
                return visitListType(ctx.listType());
            if (ctx.mapType() != null)
                return visitMapType(ctx.mapType());
            
            // Syntax error - report and return placeholder list type
            Range range = range(ctx);
            reportSyntaxError(range, "Expected LIST or MAP composite type");
            Identifier errorId = new Identifier(range, "<error>");
            TypeNode errorElementType = new ComplexTypeNode(range, QName.of(errorId));
            return new ListTypeNode(range, errorElementType);
        }

        @Override
        public ListTypeNode visitListType(SqlStreamParser.ListTypeContext ctx) {
            return new ListTypeNode(range(ctx), visitType(ctx.type()));
        }

        @Override
        public MapTypeNode visitMapType(SqlStreamParser.MapTypeContext ctx) {
            PrimitiveTypeNode key = visitPrimitiveType(ctx.primitiveType());
            TypeNode value = visitType(ctx.type());
            return new MapTypeNode(range(ctx), key, value);
        }

        @Override
        public ComplexTypeNode visitTypeReference(SqlStreamParser.TypeReferenceContext ctx) {
            return new ComplexTypeNode(range(ctx), visitQname(ctx.qname()));
        }

        // ========================================================================
        // ScalarDecl
        // ========================================================================

        @Override
        public ScalarDecl visitScalarDecl(SqlStreamParser.ScalarDeclContext ctx) {
            Range range = range(ctx);
            TypeNode type = visitType(ctx.type());
            return new ScalarDecl(range, type);
        }

        @Override
        public CheckNode visitCheckFragment(SqlStreamParser.CheckFragmentContext ctx) {
            Range range = range(ctx);
            Expr expr = visitExpr(ctx.expr());
            return new CheckNode(range, expr);
        }

        // ========================================================================
        // EnumDecl
        // ========================================================================

        @Override
        public EnumDecl visitEnumDecl(SqlStreamParser.EnumDeclContext ctx) {
            Range range = range(ctx);
            AstOptionalNode<TypeNode> base = visitEnumType(ctx.enumType());
            AstListNode<EnumSymbolDecl> symbols = visitEnumSymbolList(ctx.enumSymbolList());
            return new EnumDecl(
                range,
                base,
                symbols
            );
        }

        @Override
        public AstOptionalNode<TypeNode> visitEnumType(SqlStreamParser.EnumTypeContext ctx) {
            if (ctx.type() == null)
                return AstOptionalNode.empty(TypeNode.class);
            TypeNode type = visitType(ctx.type());
            return AstOptionalNode.of(type, TypeNode.class);
        }

        @Override
        public AstListNode<EnumSymbolDecl> visitEnumSymbolList(SqlStreamParser.EnumSymbolListContext ctx) {
            AstListNode<EnumSymbolDecl> symbols = new AstListNode<>(EnumSymbolDecl.class);
            for (SqlStreamParser.EnumSymbolContext esc : ctx.enumSymbol())
                symbols.add(visitEnumSymbol(esc));
            return symbols;
        }

        @Override
        public EnumSymbolDecl visitEnumSymbol(SqlStreamParser.EnumSymbolContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            ConstExpr value = visitConstExpr(ctx.constExpr());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            return new EnumSymbolDecl(range, name, value, fragments);
        }

        // ========================================================================
        // CONST EXPRESSIONS
        // ========================================================================

        @Override
        public ConstExpr visitConstTerm(SqlStreamParser.ConstTermContext ctx) {

            if (ctx.NUMBER_LIT() != null) {
                return new ConstLiteralExpr(
                    range(ctx),
                    ctx.NUMBER_LIT().getText()
                );
            }

            if (ctx.constSymbolRef() != null) {
                return visitConstSymbolRef(ctx.constSymbolRef());
            }

            if (ctx.constExpr() != null) {
                // Parenthesized expression
                ConstExpr inner = visitConstExpr(ctx.constExpr());
                return new ConstParenExpr(range(ctx), inner);
            }

            // Syntax error - report and return placeholder literal
            Range range = range(ctx);
            reportSyntaxError(range, "Expected constant literal, symbol reference, or parenthesized expression");
            return new ConstLiteralExpr(range, "0");
        }

        @Override
        public ConstExpr visitConstSymbolRef(SqlStreamParser.ConstSymbolRefContext ctx) {
            Identifier id = new Identifier(range(ctx), ctx.identifier().getText());
            return new ConstSymbolRefExpr(range(ctx), id);
        }

        @Override
        public ConstExpr visitConstExpr(SqlStreamParser.ConstExprContext ctx) {

            ConstExpr left = visitConstTerm(ctx.constTerm(0));

            int opCount = ctx.getChildCount() - 1;
            if (opCount <= 0)
                return left;

            int termIndex = 1;
            for (int i = 1; i < ctx.getChildCount(); i += 2) {
                Token tok = (Token) ctx.getChild(i).getPayload();
                ConstBinaryOp op = toConstOp(tok);

                ConstExpr right = visitConstTerm(ctx.constTerm(termIndex++));
                left = new ConstBinaryExpr(range(ctx), left, right, op);
            }

            return left;
        }

        private ConstBinaryOp toConstOp(Token tok) {
            return switch (tok.getType()) {

                case SqlStreamLexer.PLUS      -> ConstBinaryOp.ADD;
                case SqlStreamLexer.MINUS     -> ConstBinaryOp.SUB;
                case SqlStreamLexer.STAR      -> ConstBinaryOp.MUL;
                case SqlStreamLexer.SLASH     -> ConstBinaryOp.DIV;
                case SqlStreamLexer.PERCENT   -> ConstBinaryOp.MOD;
                case SqlStreamLexer.SHL       -> ConstBinaryOp.SHL;
                case SqlStreamLexer.SHR       -> ConstBinaryOp.SHR;
                case SqlStreamLexer.AMP       -> ConstBinaryOp.BITAND;
                case SqlStreamLexer.PIPE      -> ConstBinaryOp.BITOR;
                case SqlStreamLexer.XOR       -> ConstBinaryOp.BITXOR;
                // Syntax error - report and return placeholder operator
                default -> {
                    Range tokRange = new Range(
                        _source,
                        new Pos(tok.getLine(), tok.getCharPositionInLine()),
                        new Pos(tok.getLine(), tok.getCharPositionInLine() + tok.getText().length())
                    );
                    reportSyntaxError(tokRange, "Unknown operator: " + tok.getText());
                    yield ConstBinaryOp.ADD;
                }
            };
        }
        
        // ========================================================================
        // StructDecl
        // ========================================================================

        @Override
        public StructDecl visitStructDecl(SqlStreamParser.StructDeclContext ctx) {
            Range range = range(ctx);
            AstListNode<StructFieldDecl> fields = visitFieldList(ctx.fieldList());
            return new StructDecl(range, fields);
        }

        @Override
        public AstListNode<StructFieldDecl> visitFieldList(SqlStreamParser.FieldListContext ctx) {
            AstListNode<StructFieldDecl> fields = new AstListNode<>(StructFieldDecl.class);
            for (SqlStreamParser.FieldDeclContext fdc : ctx.fieldDecl())
                fields.add(visitFieldDecl(fdc));
            return fields;
        }

        @Override
        public StructFieldDecl visitFieldDecl(SqlStreamParser.FieldDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            TypeNode type = visitType(ctx.type());
            AstOptionalNode<NullLiteralNode> nullable = visitNullableMarker(ctx.nullableMarker());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());

            return new StructFieldDecl(range, name, type, nullable, fragments);
        }

        @Override
        public AstOptionalNode<NullLiteralNode> visitNullableMarker(SqlStreamParser.NullableMarkerContext ctx) {
            if (ctx == null)
                return AstOptionalNode.empty(NullLiteralNode.class);
            NullLiteralNode value = new NullLiteralNode(range(ctx));
            return AstOptionalNode.of(value, NullLiteralNode.class);
        }

        // ========================================================================
        // UnionDecl
        // ========================================================================

        @Override
        public UnionDecl visitUnionDecl(SqlStreamParser.UnionDeclContext ctx) {
            Range range = range(ctx);
            AstListNode<UnionMemberDecl> members = visitUnionMemberList(ctx.unionMemberList());
            return new UnionDecl(range, members);
        }

        @Override
        public AstListNode<UnionMemberDecl> visitUnionMemberList(SqlStreamParser.UnionMemberListContext ctx) {
            AstListNode<UnionMemberDecl> members = new AstListNode<>(UnionMemberDecl.class);
            for (SqlStreamParser.UnionMemberDeclContext umdc : ctx.unionMemberDecl())
                members.add(visitUnionMemberDecl(umdc));
            return members;
        }

        @Override
        public UnionMemberDecl visitUnionMemberDecl(SqlStreamParser.UnionMemberDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            TypeNode type = visitType(ctx.type());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            return new UnionMemberDecl(range, name, type, fragments);
        }

        // ========================================================================
        // TYPE ALIAS
        // ========================================================================

        @Override
        public DerivedTypeDecl visitDerivedType(SqlStreamParser.DerivedTypeContext ctx) {
            Range range = range(ctx);
            ComplexTypeNode targetName = visitTypeReference(ctx.typeReference());
            return new DerivedTypeDecl(range, targetName);
        }

        // ========================================================================
        // STREAMS
        // ========================================================================

        @Override
        public StreamDecl visitStreamDecl(SqlStreamParser.StreamDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitStreamName(ctx.streamName());
            AstListNode<StreamMemberDecl> members = visitStreamTypeDeclList(ctx.streamTypeDeclList());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            return new StreamDecl(range, name, members, fragments);
        }

        @Override
        public Identifier visitStreamName(SqlStreamParser.StreamNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public AstListNode<StreamMemberDecl> visitStreamTypeDeclList(SqlStreamParser.StreamTypeDeclListContext ctx) {
            AstListNode<StreamMemberDecl> members = new AstListNode<>(StreamMemberDecl.class);
            for (SqlStreamParser.StreamTypeDeclContext stdc : ctx.streamTypeDecl())
                members.add(visitStreamTypeDecl(stdc));
            return members;
        }

        @Override
        public StreamMemberDecl visitStreamTypeDecl(SqlStreamParser.StreamTypeDeclContext ctx) {
            Range range = range(ctx);
            TypeDecl memberDecl = visitTypeDecl(ctx.typeDecl());
            AstListNode<DeclFragment> fragments = visitDeclTailFragments(ctx.declTailFragments());
            
            return new StreamMemberDecl(
                range,
                memberDecl,
                fragments
            );
        }

        @Override
        public DistributeDecl visitDistributeFragment(SqlStreamParser.DistributeFragmentContext ctx) {
            Range range = range(ctx);
            AstListNode<Identifier> fields = new AstListNode<>(Identifier.class);
            for (SqlStreamParser.IdentifierContext ic : ctx.identifier())
                fields.add(visitIdentifier(ic));
            return new DistributeDecl(range, fields);
        }

        @Override
        public TimestampDecl visitTimestampFragment(SqlStreamParser.TimestampFragmentContext ctx) {
            Range range = range(ctx);
            Identifier field = visitIdentifier(ctx.identifier());
            return new TimestampDecl(range, field);
        }

        // ========================================================================
        // READ / WRITE
        // ========================================================================

        @Override
        public ReadStmt visitReadStmt(SqlStreamParser.ReadStmtContext ctx) {
            Range range = range(ctx);
            QName stream = visitQname(ctx.qname());
            AstListNode<ReadTypeBlock> blocks = visitReadBlockList(ctx.readBlockList());
            return new ReadStmt(range, stream, blocks);
        }

        @Override
        public AstListNode<ReadTypeBlock> visitReadBlockList(SqlStreamParser.ReadBlockListContext ctx) {
            AstListNode<ReadTypeBlock> blocks = new AstListNode<>(ReadTypeBlock.class);
            for (SqlStreamParser.ReadBlockContext rbc : ctx.readBlock())
                blocks.add(visitReadBlock(rbc));
            return blocks;
        }

        @Override
        public ReadTypeBlock visitReadBlock(SqlStreamParser.ReadBlockContext ctx) {
            Range range = range(ctx);
            Identifier alias = visitTypeName(ctx.typeName());
            ProjectionNode proj = visitReadProjection(ctx.readProjection());
            AstOptionalNode<WhereNode> where = visitWhereClause(ctx.whereClause());
            return new ReadTypeBlock(range, alias, proj, where);
        }

        public ProjectionNode visitReadProjection(SqlStreamParser.ReadProjectionContext ctx) {
            Range range = range(ctx);
            AstListNode<ProjectionExprNode> items = new AstListNode<>(ProjectionExprNode.class);
            if (ctx.STAR() == null)
                items = visitReadProjectionList(ctx.readProjectionList());
            return new ProjectionNode(range, items);
        }

        @Override
        public AstListNode <ProjectionExprNode> visitReadProjectionList(SqlStreamParser.ReadProjectionListContext ctx) {
            AstListNode<ProjectionExprNode> items = new AstListNode<>(ProjectionExprNode.class);
            for (SqlStreamParser.ReadProjectionExprContext pex : ctx.readProjectionExpr())
                items.add(visitReadProjectionExpr(pex));
            return items;
        }

        public ProjectionExprNode visitReadProjectionExpr(SqlStreamParser.ReadProjectionExprContext ctx) {
            Range range = range(ctx);
            Expr e = visitExpr(ctx.expr());
            AstOptionalNode<Identifier> alias = visitFieldAlias(ctx.fieldAlias());
            return new ProjectionExprNode(range, e, alias);
        }

        public AstOptionalNode<Identifier> visitFieldAlias(SqlStreamParser.FieldAliasContext ctx) {
            if (ctx == null)
                return AstOptionalNode.empty(Identifier.class);
            Identifier identifier = visitIdentifier(ctx.identifier());
            return AstOptionalNode.of(identifier, Identifier.class);
        }

        public AstOptionalNode<WhereNode> visitWhereClause(SqlStreamParser.WhereClauseContext ctx) {
            if (ctx == null)
                return AstOptionalNode.empty(WhereNode.class);
            WhereNode node = new WhereNode(range(ctx), visitExpr(ctx.expr()));
            return AstOptionalNode.of(node, WhereNode.class);
        }

        @Override
        public WriteStmt visitWriteStmt(SqlStreamParser.WriteStmtContext ctx) {
            Range range = range(ctx);
            QName stream = visitQname(ctx.qname());
            Identifier alias = visitTypeName(ctx.typeName());
            AstListNode<StructLiteralNode> values = visitWriteValueList(ctx.writeValueList());
            return new WriteStmt(range, stream, alias, values);
        }

        @Override
        public AstListNode<StructLiteralNode> visitWriteValueList(SqlStreamParser.WriteValueListContext ctx) {
            AstListNode<StructLiteralNode> values = new AstListNode<>(StructLiteralNode.class);
            for (SqlStreamParser.StructLiteralContext slc : ctx.structLiteral())
                values.add(visitStructLiteral(slc));
            return values;
        }

        // ========================================================================
        // EXPRESSIONS
        // ========================================================================

        @Override
        public Expr visitExpr(SqlStreamParser.ExprContext ctx) {
            return visitOrExpr(ctx.orExpr());
        }

        @Override
        public Expr visitOrExpr(SqlStreamParser.OrExprContext ctx) {
            AstListNode<Expr> exprs = new AstListNode<>(Expr.class);
            for (SqlStreamParser.AndExprContext ac : ctx.andExpr()) {
                exprs.add(visitAndExpr(ac));
            }
            if (exprs.size() == 1)
                return exprs.getFirst();
            return fold(exprs, InfixOp.OR);
        }

        @Override
        public Expr visitConcatExpr(SqlStreamParser.ConcatExprContext ctx) {
            AstListNode<Expr> exprs = new AstListNode<>(Expr.class);
            for (SqlStreamParser.ShiftExprContext sc : ctx.shiftExpr()) {
                exprs.add(visitShiftExpr(sc));
            }
            if (exprs.size() == 1)
                return exprs.getFirst();
            return fold(exprs, InfixOp.CONCAT);
        }

        @Override
        public Expr visitAndExpr(SqlStreamParser.AndExprContext ctx) {
            AstListNode<Expr> exprs = new AstListNode<>(Expr.class);
            for (SqlStreamParser.NotExprContext nc : ctx.notExpr()) {
                exprs.add(visitNotExpr(nc));
            }
            if (exprs.size() == 1)
                return exprs.getFirst();
            return fold(exprs, InfixOp.AND);
        }

        @Override
        public Expr visitNotExpr(SqlStreamParser.NotExprContext ctx) {
            if (ctx.NOT() != null) {
                Range range = range(ctx);
                Expr inner = visitNotExpr(ctx.notExpr());
                return new PrefixExpr(range, PrefixOp.NOT, inner);
            }
            return visitCmpExpr(ctx.cmpExpr());
        }

        @Override
        public Expr visitCmpExpr(SqlStreamParser.CmpExprContext ctx) {
            if (ctx == null) {
                // Should not happen - indicates parser generated invalid context
                Range errorRange = new Range("<error>", -1, -1, -1, -1);
                reportSyntaxError(errorRange, "Invalid expression context - possible syntax error with parentheses or operators");
                return new LiteralExpr(errorRange, new NullLiteralNode(errorRange));
            }
            if (ctx.concatExpr(0) == null) {
                Range range = range(ctx);
                reportSyntaxError(range, "Expected expression");
                return new LiteralExpr(range, new NullLiteralNode(range));
            }
            Expr result = visitConcatExpr(ctx.concatExpr(0));
            int concatIndex = 1;

            for (int i = 1; i < ctx.getChildCount(); i++) {
                var child = ctx.getChild(i);
                String text = child.getText();

                if (child instanceof TerminalNode) {
                    String up = text.toUpperCase(Locale.ROOT);

                    switch (up) {
                        case "=" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.EQ, result, right);
                        }
                        case "<>" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.NEQ, result, right);
                        }
                        case "<" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.LT, result, right);
                        }
                        case "<=" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.LTE, result, right);
                        }
                        case ">" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.GT, result, right);
                        }
                        case ">=" -> {
                            Expr right = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            result = mkInfix(InfixOp.GTE, result, right);
                        }
                        case "IS" -> {
                            String next = ctx.getChild(i + 1).getText().toUpperCase(Locale.ROOT);
                            if (next.equals("NULL")) {
                                Range range = new Range(_source, result.range().from(), end(ctx));
                                result = new PostfixExpr(range, PostfixOp.IS_NULL, result);
                                i += 1;
                            } else if (next.equals("NOT")) {
                                String next2 = ctx.getChild(i + 2).getText().toUpperCase(Locale.ROOT);
                                if (next2.equals("NULL")) {
                                    Range range = new Range(_source, result.range().from(), end(ctx));
                                    result = new PostfixExpr(range, PostfixOp.IS_NOT_NULL, result);
                                    i += 2;
                                } else {
                                    // Syntax error - skip unsupported IS expression
                                    i += 1;
                                }
                            }
                        }
                        case "BETWEEN" -> {
                            Expr lower = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            // skip AND
                            Expr upper = visitConcatExpr(ctx.concatExpr(concatIndex++));
                            Range range = new Range(_source, result.range().from(), upper.range().to());
                            result = new TrifixExpr(range, TernaryOp.BETWEEN, result, lower, upper);
                        }
                        case "IN" -> {
                            List<SqlStreamParser.LiteralContext> lits = ctx.literal();
                            if (lits == null || lits.isEmpty()) {
                                // Syntax error - skip IN without literals
                                continue;
                            }
                            AstListNode<LiteralNode> values = new AstListNode<>(LiteralNode.class);
                            for (SqlStreamParser.LiteralContext lc : lits) {
                                values.add(visitLiteral(lc));
                            }
                            Range listRange = new Range(
                                    _source,
                                    start(lits.getFirst()),
                                    end(lits.getLast()));
                            ListLiteralNode listLit = new ListLiteralNode(listRange, values);
                            Range range = new Range(_source, result.range().from(), listRange.to());
                            result = new InfixExpr(range, InfixOp.IN, result, new LiteralExpr(listRange, listLit));
                        }
                        default -> {
                            // ignore other terminals handled by grammar (AND, LPAREN, etc.)
                        }
                    }
                }
            }

            return result;
        }

        @Override
        public Expr visitShiftExpr(SqlStreamParser.ShiftExprContext ctx) {
            AstListNode<Expr> parts = new AstListNode<>(Expr.class);
            for (SqlStreamParser.AddExprContext ac : ctx.addExpr()) {
                parts.add(visitAddExpr(ac));
            }
            if (parts.size() == 1)
                return parts.getFirst();

            Expr res = parts.getFirst();
            int idx = 1;
            for (int i = 1; i < ctx.getChildCount(); i++) {
                String op = ctx.getChild(i).getText();
                if (op.equals("<<") || op.equals(">>")) {
                    Expr right = parts.get(idx++);
                    InfixOp bop = op.equals("<<") ? InfixOp.SHL : InfixOp.SHR;
                    res = mkInfix(bop, res, right);
                }
            }
            return res;
        }

        @Override
        public Expr visitAddExpr(SqlStreamParser.AddExprContext ctx) {
            AstListNode<Expr> parts = new AstListNode<>(Expr.class);
            for (SqlStreamParser.MulExprContext mc : ctx.mulExpr()) {
                parts.add(visitMulExpr(mc));
            }
            if (parts.size() == 1)
                return parts.getFirst();

            Expr res = parts.getFirst();
            int idx = 1;
            for (int i = 1; i < ctx.getChildCount(); i++) {
                String op = ctx.getChild(i).getText();
                if (op.equals("+") || op.equals("-")) {
                    Expr right = parts.get(idx++);
                    InfixOp bop = op.equals("+") ? InfixOp.ADD : InfixOp.SUB;
                    res = mkInfix(bop, res, right);
                }
            }
            return res;
        }

        @Override
        public Expr visitMulExpr(SqlStreamParser.MulExprContext ctx) {
            AstListNode<Expr> parts = new AstListNode<>(Expr.class);
            for (SqlStreamParser.UnaryExprContext uc : ctx.unaryExpr()) {
                parts.add(visitUnaryExpr(uc));
            }
            if (parts.size() == 1)
                return parts.get(0);

            Expr res = parts.get(0);
            int idx = 1;

            for (int i = 1; i < ctx.getChildCount(); i++) {
                String op = ctx.getChild(i).getText();

                // ONLY accept *, /, %
                switch (op) {
                    case "*" -> {
                        Expr right = parts.get(idx++);
                        res = mkInfix(InfixOp.MUL, res, right);
                    }
                    case "/" -> {
                        Expr right = parts.get(idx++);
                        res = mkInfix(InfixOp.DIV, res, right);
                    }
                    case "%" -> {
                        Expr right = parts.get(idx++);
                        res = mkInfix(InfixOp.MOD, res, right);
                    }
                    default -> {
                        // ignore garbage like AS, identifiers, whitespace, newlines
                    }
                }
            }

            return res;
        }

        @Override
        public Expr visitUnaryExpr(SqlStreamParser.UnaryExprContext ctx) {
            if (ctx.MINUS() != null) {
                Expr inner = visitUnaryExpr(ctx.unaryExpr());
                Range range = new Range(_source, inner.range().from(), inner.range().to());
                return new PrefixExpr(range, PrefixOp.NEG, inner);
            }
            return visitPostfixExpr(ctx.postfixExpr());
        }

        @Override
        public Expr visitPostfixExpr(SqlStreamParser.PostfixExprContext ctx) {
            Expr node = visitPrimary(ctx.primary());
            if (ctx.getChildCount() == 1)
                return node;

            for (int i = 1; i < ctx.getChildCount(); i++) {
                var ch = ctx.getChild(i);
                if (ch instanceof SqlStreamParser.MemberAccessContext ma) {
                    MemberExpr m = visitMemberAccess(ma);
                    node = m.withTarget(node);
                } else if (ch instanceof SqlStreamParser.IndexAccessContext ix) {
                    IndexExpr idx = visitIndexAccess(ix);
                    node = idx.withTarget(node);
                }
            }
            return node;
        }

        @Override
        public MemberExpr visitMemberAccess(SqlStreamParser.MemberAccessContext ctx) {
            Range range = range(ctx);
            Identifier member = visitIdentifier(ctx.identifier());
            return new MemberExpr(range, null, member);
        }

        @Override
        public IndexExpr visitIndexAccess(SqlStreamParser.IndexAccessContext ctx) {
            Range range = range(ctx);
            Expr index = visitExpr(ctx.expr());
            return new IndexExpr(range, null, index);
        }

        @Override
        public Expr visitPrimary(SqlStreamParser.PrimaryContext ctx) {
            if (ctx.LPAREN() != null) {
                return visitExpr(ctx.expr());
            }
            if (ctx.literal() != null) {
                LiteralNode lit = visitLiteral(ctx.literal());
                return new LiteralExpr(range(ctx), lit);
            }
            if (ctx.identifier() != null) {
                return new IdentifierExpr(range(ctx), visitIdentifier(ctx.identifier()));
            }
            
            // Syntax error - return placeholder identifier
            Range range = range(ctx);
            return new IdentifierExpr(range, new Identifier(range, "<error>"));
        }

        // ========================================================================
        // LITERALS
        // ========================================================================

        @Override
        public LiteralNode visitLiteral(SqlStreamParser.LiteralContext ctx) {
            if (ctx.NULL() != null)
                return new NullLiteralNode(range(ctx));
            if (ctx.literalValue() != null)
                return visitLiteralValue(ctx.literalValue());
            if (ctx.structLiteral() != null)
                return visitStructLiteral(ctx.structLiteral());
            if (ctx.enumLiteral() != null)
                return visitEnumLiteral(ctx.enumLiteral());
            if (ctx.unionLiteral() != null)
                return visitUnionLiteral(ctx.unionLiteral());
            if (ctx.listLiteral() != null)
                return visitListLiteral(ctx.listLiteral());
            if (ctx.mapLiteral() != null)
                return visitMapLiteral(ctx.mapLiteral());
            
            // Syntax error - report and return placeholder null literal
            Range range = range(ctx);
            reportSyntaxError(range, "Expected literal value (null, boolean, number, string, bytes, struct, enum, union, list, or map)");
            return new NullLiteralNode(range);
        }

        @Override
        public PrimitiveLiteralNode visitLiteralValue(SqlStreamParser.LiteralValueContext ctx) {
            Range range = range(ctx);
            if (ctx.TRUE() != null)
                return new BoolLiteralNode(range, true);
            if (ctx.FALSE() != null)
                return new BoolLiteralNode(range, false);
            if (ctx.STRING_LIT() != null) {
                String raw = ctx.STRING_LIT().getText();
                return new StringLiteralNode(range, unquote(raw));
            }
            if (ctx.NUMBER_LIT() != null) {
                return new NumberLiteralNode(range, ctx.NUMBER_LIT().getText());
            }
            if (ctx.BYTES_LIT() != null) {
                return new BytesLiteralNode(range, ctx.BYTES_LIT().getText());
            }
            
            // Syntax error - report and return placeholder string literal
            reportSyntaxError(range, "Expected literal value (true, false, string, number, or bytes)");
            return new StringLiteralNode(range, "");
        }

        @Override
        public ListLiteralNode visitListLiteral(SqlStreamParser.ListLiteralContext ctx) {
            AstListNode<LiteralNode> elems = new AstListNode<>(LiteralNode.class);
            for (SqlStreamParser.LiteralContext lc : ctx.literal()) {
                elems.add(visitLiteral(lc));
            }
            return new ListLiteralNode(range(ctx), elems);
        }

        @Override
        public MapLiteralNode visitMapLiteral(SqlStreamParser.MapLiteralContext ctx) {
            AstListNode<MapEntryLiteralNode> entries = new AstListNode<>(MapEntryLiteralNode.class);
            for (SqlStreamParser.MapEntryContext mc : ctx.mapEntry()) {
                entries.add(visitMapEntry(mc));
            }
            return new MapLiteralNode(range(ctx), entries);
        }

        @Override
        public MapEntryLiteralNode visitMapEntry(SqlStreamParser.MapEntryContext ctx) {
            Range range = range(ctx);
            PrimitiveLiteralNode key = visitLiteralValue(ctx.literalValue());
            LiteralNode value = visitLiteral(ctx.literal());
            return new MapEntryLiteralNode(range, key, value);
        }

        @Override
        public StructLiteralNode visitStructLiteral(SqlStreamParser.StructLiteralContext ctx) {
            AstListNode<StructFieldLiteralNode> fields = new AstListNode<>(StructFieldLiteralNode.class);
            for (SqlStreamParser.StructEntryContext sc : ctx.structEntry()) {
                fields.add(visitStructEntry(sc));
            }
            return new StructLiteralNode(range(ctx), fields);
        }

        @Override
        public StructFieldLiteralNode visitStructEntry(SqlStreamParser.StructEntryContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            LiteralNode value = visitLiteral(ctx.literal());
            return new StructFieldLiteralNode(range, name, value);
        }

        @Override
        public UnionLiteralNode visitUnionLiteral(SqlStreamParser.UnionLiteralContext ctx) {
            Range range = range(ctx);
            QName unionName = visitQname(ctx.qname());
            Identifier memberName = visitIdentifier(ctx.identifier());
            LiteralNode value = visitLiteral(ctx.literal());
            return new UnionLiteralNode(range, unionName, memberName, value);
        }

        @Override
        public EnumLiteralNode visitEnumLiteral(SqlStreamParser.EnumLiteralContext ctx) {
            Range range = range(ctx);
            QName enumName = visitQname(ctx.qname());
            Identifier symbol = visitIdentifier(ctx.identifier());
            return new EnumLiteralNode(range, enumName, symbol);
        }

        // ========================================================================
        // Helpers
        // ========================================================================

        private Range range(ParserRuleContext c) {
            var start = c.getStart();
            var stop = c.getStop();
            return new Range(
                    _source,
                    new Pos(start.getLine(), start.getCharPositionInLine()),
                    new Pos(stop.getLine(), stop.getCharPositionInLine() + stop.getText().length()));
        }

        private Pos start(ParserRuleContext c) {
            var s = c.getStart();
            return new Pos(s.getLine(), s.getCharPositionInLine());
        }

        private Pos end(ParserRuleContext c) {
            var e = c.getStop();
            return new Pos(e.getLine(), e.getCharPositionInLine() + e.getText().length());
        }

        private void reportSyntaxError(Range range, String message) {
            _diags.error(range, DiagnosticKind.PARSER, DiagnosticCode.SYNTAX_ERROR, message);
        }

        private Expr fold(AstListNode<Expr> xs, InfixOp op) {
            if (xs.isEmpty()) {
                throw new IllegalArgumentException("fold requires non-empty list");
            }
            Expr acc = xs.getFirst();
            for (int i = 1; i < xs.size(); i++) {
                Expr b = xs.get(i);
                Range range = new Range(_source, acc.range().from(), b.range().to());
                acc = new InfixExpr(range, op, acc, b);
            }
            return acc;
        }

        private InfixExpr mkInfix(InfixOp op, Expr left, Expr right) {
            Range range = new Range(_source, left.range().from(), right.range().to());
            return new InfixExpr(range, op, left, right);
        }

        private String unquote(String s) {
            if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
                return s.substring(1, s.length() - 1).replace("''", "'");
            }
            return s;
        }

        public static String normalizeDoc(String raw, int closingIndent) {
            if (raw == null || raw.isEmpty())
                return "";

            // remove leading/trailing blank lines
            raw = raw.replaceAll("^\n+", "").replaceAll("\n+$", "");

            String[] lines = raw.split("\n", -1);
            StringBuilder sb = new StringBuilder();

            if (lines.length == 0)
                return "";
            var line = lines[0];
            var stripped = stripIndent(line, closingIndent);
            sb.append(stripped);
            for (int i = 1; i < lines.length; i++) {
                sb.append("\n");
                line = lines[i];
                stripped = stripIndent(line, closingIndent);
                sb.append(stripped);
            }

            return sb.toString();
        }

        private static String stripIndent(String line, int indent) {
            int actual = countLeadingSpaces(line);
            if (actual >= indent)
                return line.substring(indent);
            else
                return line.trim().isEmpty() ? "" : line; // preserve non-empty short lines
        }

        private static int countLeadingSpaces(String s) {
            int n = 0;
            while (n < s.length() && s.charAt(n) == ' ')
                n++;
            return n;
        }

        private String extractTripleQuotedInner(String text) {
            // strip the opening and closing single quotes
            return text.substring(1, text.length() - 1).strip();
        }
    }
}