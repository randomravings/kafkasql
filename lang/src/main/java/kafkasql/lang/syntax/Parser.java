package kafkasql.lang.syntax;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Pos;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.parse.SqlStreamParser;
import kafkasql.lang.parse.SqlStreamParserBaseVisitor;
import kafkasql.lang.parse.SqlStreamParser.IncludePragmaContext;
import kafkasql.lang.syntax.ast.*;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberInlineDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberRefDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DistributeNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.fragment.ProjectionExprNode;
import kafkasql.lang.syntax.ast.fragment.ProjectionNode;
import kafkasql.lang.syntax.ast.fragment.WhereNode;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.misc.*;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.type.*;
import kafkasql.lang.syntax.ast.use.ContextUse;

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
        AstBuilder builder = new AstBuilder(args.source());
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
        AstBuilder builder = new AstBuilder(args.source());
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
        public AstBuilder(String source) {
            this._source = source;
        }

        // ========================================================================
        // Top-level: script / statements
        // ========================================================================

        @Override
        public Script visitScript(SqlStreamParser.ScriptContext ctx) {
            TypedList<Include> includes = new TypedList<>(Include.class);
            TypedList<Stmt> stmts = new TypedList<>(Stmt.class);
            if (ctx.includeSection() != null)
                for (IncludePragmaContext inc : ctx.includeSection().includePragma())
                    includes.add(visitIncludePragma(inc));
            for (SqlStreamParser.StatementContext sc : ctx.statement()) {
                stmts.add(visitStatement(sc));
            }
            return new Script(range(ctx), includes, stmts);
        }

        @Override
        public Include visitIncludePragma(SqlStreamParser.IncludePragmaContext ctx) {
            Range range = range(ctx);
            String path = unquote(ctx.STRING_LIT().getText());
            return new Include(range, path);
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
            throw new IllegalStateException("Unknown statement kind: " + ctx.getText());
        }

        // ========================================================================
        // Documentation comments
        // ========================================================================

        @Override
        public DocNode visitCommentClause(SqlStreamParser.CommentClauseContext ctx) {
            Range range = range(ctx);
            String raw = ctx.STRING_LIT().getText();
            int closingIndent = ctx.COMMENT().getSymbol().getCharPositionInLine();
            String inner = extractTripleQuotedInner(raw);
            String norm = normalizeDoc(inner, closingIndent);
            return new DocNode(range, norm);
        }

        private <C extends ParserRuleContext, T extends AstNode> TypedOptional<T> opt(
                C ctx,
                Function<C, T> fn,
                Class<T> clazz) {
            if (ctx == null)
                return TypedOptional.empty(clazz);
            return TypedOptional.of(fn.apply(ctx), clazz);
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
            throw new IllegalStateException("Unknown decl: " + ctx.getText());
        }

        @Override
        public ContextDecl visitContextDecl(SqlStreamParser.ContextDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitContextName(ctx.contextName());
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);
            for (SqlStreamParser.ContextTailContext tc : ctx.contextTail()) {
                if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in context: " + name.name());
                    }
                    var value = visitCommentClause(tc.commentClause());
                    doc = TypedOptional.of(value, DocNode.class);
                }
            }
            return new ContextDecl(range, name, doc);
        }

        @Override
        public Identifier visitContextName(SqlStreamParser.ContextNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public TypeDecl visitTypeDecl(SqlStreamParser.TypeDeclContext ctx) {
            if (ctx.scalarDecl() != null)
                return visitScalarDecl(ctx.scalarDecl());
            if (ctx.enumDecl() != null)
                return visitEnumDecl(ctx.enumDecl());
            if (ctx.structDecl() != null)
                return visitStructDecl(ctx.structDecl());
            if (ctx.unionDecl() != null)
                return visitUnionDecl(ctx.unionDecl());
            throw new IllegalStateException("Unknown typeDecl: " + ctx.getText());
        }

        // ========================================================================
        // QNAME / IDENTIFIER
        // ========================================================================

        @Override
        public QName visitQname(SqlStreamParser.QnameContext ctx) {
            Range range = range(ctx);
            TypedList<Identifier> parts = new TypedList<>(Identifier.class);
            for (SqlStreamParser.IdentifierContext ic : ctx.identifier()) {
                parts.add(visitIdentifier(ic));
            }
            return new QName(range, parts);
        }

        @Override
        public Identifier visitIdentifier(SqlStreamParser.IdentifierContext ctx) {
            return new Identifier(range(ctx), ctx.ID().getText());
        }

        @Override
        public AstNode visitDotPrefix(SqlStreamParser.DotPrefixContext ctx) {
            // Dot prefix is not modeled in AST (QName.parts already holds the parts).
            return null;
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
            throw new IllegalStateException("Unknown type: " + ctx.getText());
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
            throw new IllegalStateException("Unknown primitiveType: " + ctx.getText());
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
            throw new IllegalStateException("Unknown compositeType: " + ctx.getText());
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
            Identifier name = visitScalarName(ctx.scalarName());
            PrimitiveTypeNode base = visitPrimitiveType(ctx.primitiveType());
            TypedOptional<LiteralNode> defaultValue = TypedOptional.empty(LiteralNode.class);
            TypedOptional<CheckNode> check = TypedOptional.empty(CheckNode.class);
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);

            for (SqlStreamParser.ScalarTailContext tc : ctx.scalarTail()) {
                if (tc.scalarDefault() != null) {
                    if (defaultValue.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple DEFAULT clauses in scalar: " + name.name());
                    }
                    var value = visitScalarDefault(tc.scalarDefault());
                    defaultValue = TypedOptional.of(value, LiteralNode.class);
                    continue;
                }
                if (tc.scalarCheck() != null) {
                    if (check.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple CHECK clauses in scalar: " + name.name());
                    }
                    var value = visitScalarCheck(tc.scalarCheck());
                    check = TypedOptional.of(value, CheckNode.class);
                    continue;
                }
                if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in scalar: " + name.name());
                    }
                    var value = visitCommentClause(tc.commentClause());
                    doc = TypedOptional.of(value, DocNode.class);
                    continue;
                }
            }

            return new ScalarDecl(range, name, base, defaultValue, check, doc);
        }

        @Override
        public Identifier visitScalarName(SqlStreamParser.ScalarNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public LiteralNode visitScalarDefault(SqlStreamParser.ScalarDefaultContext ctx) {
            return visitLiteralValue(ctx.literalValue());
        }

        @Override
        public CheckNode visitScalarCheck(SqlStreamParser.ScalarCheckContext ctx) {
            Expr expr = visitExpr(ctx.expr());

            Identifier name = new Identifier(
                    range(ctx),
                    "scalar_check");

            return new CheckNode(
                    range(ctx),
                    name,
                    expr);
        }

        // ========================================================================
        // EnumDecl
        // ========================================================================

        @Override
        public EnumDecl visitEnumDecl(SqlStreamParser.EnumDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitEnumName(ctx.enumName());

            TypedOptional<PrimitiveTypeNode> baseType = opt(
                    ctx.enumBaseType(),
                    this::visitEnumBaseType,
                    PrimitiveTypeNode.class);

            TypedList<EnumSymbolDecl> symbols = buildEnumSymbolList(ctx.enumSymbolList());

            TypedOptional<EnumLiteralNode> defaultValue = TypedOptional.empty(EnumLiteralNode.class);
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);

            // enumTail* -> { enumDefault | commentClause }
            for (SqlStreamParser.EnumTailContext tc : ctx.enumTail()) {
                if (tc.enumDefault() != null) {
                    if (defaultValue.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple DEFAULT clauses in enum: " + name.name());
                    }
                    defaultValue = opt(
                            tc.enumDefault(),
                            this::visitEnumDefault,
                            EnumLiteralNode.class);
                } else if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in enum: " + name.name());
                    }
                    doc = opt(
                            tc.commentClause(),
                            this::visitCommentClause,
                            DocNode.class);
                }
            }

            return new EnumDecl(
                    range,
                    name,
                    baseType,
                    symbols,
                    defaultValue,
                    doc);
        }

        @Override
        public Identifier visitEnumName(SqlStreamParser.EnumNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public PrimitiveTypeNode visitEnumBaseType(SqlStreamParser.EnumBaseTypeContext ctx) {
            Range range = range(ctx);
            if (ctx.INT8() != null)
                return PrimitiveTypeNode.int8(range);
            if (ctx.INT16() != null)
                return PrimitiveTypeNode.int16(range);
            if (ctx.INT32() != null)
                return PrimitiveTypeNode.int32(range);
            if (ctx.INT64() != null)
                return PrimitiveTypeNode.int64(range);
            throw new IllegalStateException("Unknown enumBaseType: " + ctx.getText());
        }

        private TypedList<EnumSymbolDecl> buildEnumSymbolList(SqlStreamParser.EnumSymbolListContext ctx) {
            TypedList<EnumSymbolDecl> list = new TypedList<>(EnumSymbolDecl.class);
            for (SqlStreamParser.EnumSymbolContext ec : ctx.enumSymbol()) {
                list.add(visitEnumSymbol(ec));
            }
            return list;
        }

        @Override
        public EnumSymbolDecl visitEnumSymbol(SqlStreamParser.EnumSymbolContext ctx) {
            Range range = range(ctx);
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);
            Identifier name = visitIdentifier(ctx.identifier());
            NumberLiteralNode value = new NumberLiteralNode(range, ctx.NUMBER_LIT().getText());

            for (SqlStreamParser.EnumSymbolTailContext tc : ctx.enumSymbolTail()) {
                if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in enum symbol: " + name.name());
                    }
                    doc = opt(
                            tc.commentClause(),
                            this::visitCommentClause,
                            DocNode.class);
                }
            }

            return new EnumSymbolDecl(range, name, value, doc);
        }

        public EnumLiteralNode visitEnumDefault(SqlStreamParser.EnumDefaultContext ctx) {
            return visitEnumLiteral(ctx.enumLiteral());
        }

        // ========================================================================
        // StructDecl
        // ========================================================================

        @Override
        public StructDecl visitStructDecl(SqlStreamParser.StructDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitStructName(ctx.structName());
            TypedList<StructFieldDecl> fields = buildFieldList(ctx.fieldList());
            TypedList<CheckNode> checks = new TypedList<>(CheckNode.class);
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);

            for (SqlStreamParser.StructTailContext tc : ctx.structTail()) {
                if (tc.structCheck() != null) {
                    var value = visitStructCheck(tc.structCheck());
                    if (checks.stream().anyMatch(c -> c.name().name().equals(value.name().name()))) {
                        throw new IllegalStateException(
                                "Multiple CHECK clauses with same name in struct: " + name.name());
                    }
                } else if (tc.commentClause() != null) {
                    if (doc != null && doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in struct: " + name.name());
                    }
                    doc = opt(
                            tc.commentClause(),
                            this::visitCommentClause,
                            DocNode.class);
                }
            }

            return new StructDecl(range, name, fields, checks, doc);
        }

        private TypedList<StructFieldDecl> buildFieldList(SqlStreamParser.FieldListContext ctx) {
            TypedList<StructFieldDecl> result = new TypedList<>(StructFieldDecl.class);
            for (SqlStreamParser.FieldDeclContext fc : ctx.fieldDecl()) {
                result.add(visitFieldDecl(fc));
            }
            return result;
        }

        @Override
        public Identifier visitStructName(SqlStreamParser.StructNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public StructFieldDecl visitFieldDecl(SqlStreamParser.FieldDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            TypeNode type = visitType(ctx.type());
            Boolean nullable = null;
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);
            TypedOptional<LiteralNode> defaultValue = TypedOptional.empty(LiteralNode.class);

            for (SqlStreamParser.FieldTailContext tc : ctx.fieldTail()) {
                if (tc.fieldNullable() != null) {
                    if (nullable != null) {
                        throw new IllegalStateException(
                                "Multiple NULLABLE / NOT NULLABLE clauses in field: " + name.name());
                    }
                    nullable = true;
                } else if (tc.fieldDefault() != null) {
                    if (defaultValue.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple DEFAULT clauses in field: " + name.name());
                    }
                    var value = visitFieldDefault(tc.fieldDefault());
                    defaultValue = TypedOptional.of(value, LiteralNode.class);
                } else if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in field: " + name.name());
                    }
                    var value = visitCommentClause(tc.commentClause());
                    doc = TypedOptional.of(value, DocNode.class);
                }
            }

            if (nullable == null) {
                nullable = false;
            }

            return new StructFieldDecl(range, name, type, nullable, defaultValue, doc);
        }

        public LiteralNode visitFieldDefault(SqlStreamParser.FieldDefaultContext ctx) {
            return visitLiteral(ctx.literal());
        }

        @Override
        public CheckNode visitStructCheck(SqlStreamParser.StructCheckContext ctx) {
            Expr expr = visitExpr(ctx.expr());

            Identifier name = new Identifier(
                    range(ctx),
                    "struct_check");

            return new CheckNode(
                    range(ctx),
                    name,
                    expr);
        }

        // ========================================================================
        // UnionDecl
        // ========================================================================

        @Override
        public UnionDecl visitUnionDecl(SqlStreamParser.UnionDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitUnionName(ctx.unionName());
            TypedList<UnionMemberDecl> members = buildUnionMemberList(ctx.unionMemberList());
            TypedOptional<UnionLiteralNode> defaultValue = TypedOptional.empty(UnionLiteralNode.class);
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);

            for (SqlStreamParser.UnionTailContext tc : ctx.unionTail()) {
                if (tc.unionDefault() != null) {
                    if (defaultValue.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple DEFAULT clauses in union: " + name.name());
                    }
                    defaultValue = opt(
                            tc.unionDefault(),
                            this::visitUnionDefault,
                            UnionLiteralNode.class);
                } else if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in union: " + name.name());
                    }
                    doc = opt(
                            tc.commentClause(),
                            this::visitCommentClause,
                            DocNode.class);
                }
            }

            return new UnionDecl(range, name, members, defaultValue, doc);
        }

        @Override
        public Identifier visitUnionName(SqlStreamParser.UnionNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        private TypedList<UnionMemberDecl> buildUnionMemberList(SqlStreamParser.UnionMemberListContext ctx) {
            TypedList<UnionMemberDecl> result = new TypedList<>(UnionMemberDecl.class);
            for (SqlStreamParser.UnionMemberDeclContext uc : ctx.unionMemberDecl()) {
                result.add(visitUnionMemberDecl(uc));
            }
            return result;
        }

        @Override
        public UnionMemberDecl visitUnionMemberDecl(SqlStreamParser.UnionMemberDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitIdentifier(ctx.identifier());
            TypeNode type = visitType(ctx.type());
            return new UnionMemberDecl(range, name, type, TypedOptional.empty(DocNode.class));
        }

        public UnionLiteralNode visitUnionDefault(SqlStreamParser.UnionDefaultContext ctx) {
            return visitUnionLiteral(ctx.unionLiteral());
        }

        // ========================================================================
        // STREAMS
        // ========================================================================

        @Override
        public StreamDecl visitStreamDecl(SqlStreamParser.StreamDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitStreamName(ctx.streamName());
            TypedList<StreamMemberDecl> members = buildStreamTypeDeclList(ctx.streamTypeDeclList());
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);

            for (SqlStreamParser.StreamTailContext tc : ctx.streamTail()) {
                if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in stream: " + name.name());
                    }
                    doc = opt(
                            tc.commentClause(),
                            this::visitCommentClause,
                            DocNode.class);
                }
            }

            return new StreamDecl(range, name, members, doc);
        }

        @Override
        public Identifier visitStreamName(SqlStreamParser.StreamNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        private TypedList<StreamMemberDecl> buildStreamTypeDeclList(SqlStreamParser.StreamTypeDeclListContext ctx) {
            TypedList<StreamMemberDecl> result = new TypedList<>(StreamMemberDecl.class);
            for (SqlStreamParser.StreamTypeDeclContext sc : ctx.streamTypeDecl()) {
                result.add(visitStreamTypeDecl(sc));
            }
            return result;
        }

        @Override
        public StreamMemberDecl visitStreamTypeDecl(SqlStreamParser.StreamTypeDeclContext ctx) {
            Range range = range(ctx);
            Identifier name = visitStreamTypeName(ctx.streamTypeName());
            TypedOptional<DocNode> doc = TypedOptional.empty(DocNode.class);
            TypedOptional<DistributeNode> distribute = TypedOptional.empty(DistributeNode.class);
            TypedOptional<Identifier> timestampField = TypedOptional.empty(Identifier.class);
            TypedList<CheckNode> checks = new TypedList<>(CheckNode.class);

            for (SqlStreamParser.StreamTypeTailContext tc : ctx.streamTypeTail()) {
                if (tc.distributeClause() != null) {
                    if (distribute.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple DISTRIBUTE clauses in stream member: " + name.name());
                    }
                    var value = visitDistributeClause(tc.distributeClause());
                    distribute = TypedOptional.of(value, DistributeNode.class);
                } else if (tc.timestampClause() != null) {
                    if (timestampField.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple TIMESTAMP clauses in stream member: " + name.name());
                    }
                    var value = visitTimestampClause(tc.timestampClause());
                    timestampField = TypedOptional.of(value, Identifier.class);
                } else if (tc.commentClause() != null) {
                    if (doc.isPresent()) {
                        throw new IllegalStateException(
                                "Multiple COMMENT clauses in stream member: " + name.name());
                    }
                    var value = visitCommentClause(tc.commentClause());
                    doc = TypedOptional.of(value, DocNode.class);
                }
            }

            if (ctx.streamTypeRef() != null) {
                ComplexTypeNode ref = visitStreamTypeRef(ctx.streamTypeRef());
                if (checks.size() > 0) {
                    throw new IllegalStateException(
                            "CHECK clauses are not allowed in stream member ref: " + name.name());
                }
                return new StreamMemberRefDecl(range, name, ref, distribute, timestampField, checks, doc);
            }

            if (ctx.streamTypeInline() != null) {
                TypedList<StructFieldDecl> fields = buildStreamTypeInline(ctx.streamTypeInline());
                return new StreamMemberInlineDecl(range, name, fields, distribute, timestampField, checks, doc);
            }

            throw new IllegalStateException("Unknown streamTypeDecl: " + ctx.getText());
        }

        public Identifier visitStreamTypeName(SqlStreamParser.StreamTypeNameContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        @Override
        public DistributeNode visitDistributeClause(SqlStreamParser.DistributeClauseContext ctx) {
            Range range = range(ctx);
            TypedList<Identifier> keys = new TypedList<>(Identifier.class);
            for (SqlStreamParser.IdentifierContext ic : ctx.identifier()) {
                keys.add(visitIdentifier(ic));
            }
            return new DistributeNode(range, keys);
        }

        @Override
        public Identifier visitTimestampClause(SqlStreamParser.TimestampClauseContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        public ComplexTypeNode visitStreamTypeRef(SqlStreamParser.StreamTypeRefContext ctx) {
            return visitTypeReference(ctx.typeReference());
        }

        private TypedList<StructFieldDecl> buildStreamTypeInline(SqlStreamParser.StreamTypeInlineContext ctx) {
            return buildFieldList(ctx.fieldList());
        }

        // ========================================================================
        // READ / WRITE
        // ========================================================================

        @Override
        public ReadStmt visitReadStmt(SqlStreamParser.ReadStmtContext ctx) {
            Range range = range(ctx);
            QName stream = visitQname(ctx.qname());
            TypedList<ReadTypeBlock> blocks = buildReadBlockList(ctx.readBlockList());
            return new ReadStmt(range, stream, blocks);
        }

        private TypedList<ReadTypeBlock> buildReadBlockList(SqlStreamParser.ReadBlockListContext ctx) {
            TypedList<ReadTypeBlock> result = new TypedList<>(ReadTypeBlock.class);
            for (SqlStreamParser.ReadBlockContext bc : ctx.readBlock()) {
                result.add(buildReadBlock(bc));
            }
            return result;
        }

        private ReadTypeBlock buildReadBlock(SqlStreamParser.ReadBlockContext ctx) {
            Range range = range(ctx);
            Identifier alias = visitStreamTypeName(ctx.streamTypeName());
            ProjectionNode proj = visitReadProjection(ctx.readProjection());

            TypedOptional<WhereNode> where = opt(
                    ctx.whereClause(),
                    this::visitWhereClause,
                    WhereNode.class);

            return new ReadTypeBlock(range, alias, proj, where);
        }

        public ProjectionNode visitReadProjection(SqlStreamParser.ReadProjectionContext ctx) {
            Range range = range(ctx);
            TypedList<ProjectionExprNode> items = new TypedList<>(ProjectionExprNode.class);

            if (ctx.STAR() == null)
                for (SqlStreamParser.ReadProjectionExprContext ec : ctx.readProjectionExpr())
                    items.add(visitReadProjectionExpr(ec));

            return new ProjectionNode(range, items);
        }

        public ProjectionExprNode visitReadProjectionExpr(SqlStreamParser.ReadProjectionExprContext ctx) {
            Range range = range(ctx);
            Expr e = visitExpr(ctx.expr());

            TypedOptional<Identifier> alias = opt(
                    ctx.fieldAlias(),
                    this::visitFieldAlias,
                    Identifier.class);

            return new ProjectionExprNode(range, e, alias);
        }

        public Identifier visitFieldAlias(SqlStreamParser.FieldAliasContext ctx) {
            return visitIdentifier(ctx.identifier());
        }

        public WhereNode visitWhereClause(SqlStreamParser.WhereClauseContext ctx) {
            return new WhereNode(range(ctx), visitExpr(ctx.expr()));
        }

        @Override
        public WriteStmt visitWriteStmt(SqlStreamParser.WriteStmtContext ctx) {
            Range range = range(ctx);
            QName stream = visitQname(ctx.qname());
            Identifier alias = visitStreamTypeName(ctx.streamTypeName());
            TypedList<StructLiteralNode> values = buildWriteValues(ctx.writeValues());
            return new WriteStmt(range, stream, alias, values);
        }

        private TypedList<StructLiteralNode> buildWriteValues(SqlStreamParser.WriteValuesContext ctx) {
            TypedList<StructLiteralNode> result = new TypedList<>(StructLiteralNode.class);
            for (SqlStreamParser.StructLiteralContext sc : ctx.structLiteral()) {
                result.add(visitStructLiteral(sc));
            }
            return result;
        }

        // ========================================================================
        // CHECK
        // ========================================================================

        @Override
        public CheckNode visitCheckExpr(SqlStreamParser.CheckExprContext ctx) {
            Range range = range(ctx);
            Identifier name = visitCheckExprName(ctx.checkExprName());
            Expr expr = visitExpr(ctx.expr());
            return new CheckNode(range, name, expr);
        }

        @Override
        public Identifier visitCheckExprName(SqlStreamParser.CheckExprNameContext ctx) {
            return visitIdentifier(ctx.identifier());
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
            TypedList<Expr> exprs = new TypedList<>(Expr.class);
            for (SqlStreamParser.AndExprContext ac : ctx.andExpr()) {
                exprs.add(visitAndExpr(ac));
            }
            if (exprs.size() == 1)
                return exprs.getFirst();
            return fold(exprs, InfixOp.OR);
        }

        @Override
        public Expr visitAndExpr(SqlStreamParser.AndExprContext ctx) {
            TypedList<Expr> exprs = new TypedList<>(Expr.class);
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
            Expr result = visitShiftExpr(ctx.shiftExpr(0));
            int shiftIndex = 1;

            for (int i = 1; i < ctx.getChildCount(); i++) {
                var child = ctx.getChild(i);
                String text = child.getText();

                if (child instanceof TerminalNode) {
                    String up = text.toUpperCase(Locale.ROOT);

                    switch (up) {
                        case "=" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            result = mkInfix(InfixOp.EQ, result, right);
                        }
                        case "<>" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            result = mkInfix(InfixOp.NEQ, result, right);
                        }
                        case "<" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            result = mkInfix(InfixOp.LT, result, right);
                        }
                        case "<=" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            result = mkInfix(InfixOp.LTE, result, right);
                        }
                        case ">" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            result = mkInfix(InfixOp.GT, result, right);
                        }
                        case ">=" -> {
                            Expr right = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
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
                                    throw new IllegalStateException("Unsupported IS expression");
                                }
                            }
                        }
                        case "BETWEEN" -> {
                            Expr lower = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            // skip AND
                            Expr upper = visitShiftExpr(ctx.shiftExpr(shiftIndex++));
                            Range range = new Range(_source, result.range().from(), upper.range().to());
                            result = new TrifixExpr(range, TernaryOp.BETWEEN, result, lower, upper);
                        }
                        case "IN" -> {
                            List<SqlStreamParser.LiteralContext> lits = ctx.literal();
                            if (lits == null || lits.isEmpty()) {
                                throw new IllegalStateException("IN without literals");
                            }
                            TypedList<LiteralNode> values = new TypedList<>(LiteralNode.class);
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
            TypedList<Expr> parts = new TypedList<>(Expr.class);
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
            TypedList<Expr> parts = new TypedList<>(Expr.class);
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
            TypedList<Expr> parts = new TypedList<>(Expr.class);
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
            throw new IllegalStateException("Unknown primary: " + ctx.getText());
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
            throw new IllegalStateException("Unknown literal: " + ctx.getText());
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
            throw new IllegalStateException("Unknown literalValue: " + ctx.getText());
        }

        @Override
        public ListLiteralNode visitListLiteral(SqlStreamParser.ListLiteralContext ctx) {
            TypedList<LiteralNode> elems = new TypedList<>(LiteralNode.class);
            for (SqlStreamParser.LiteralContext lc : ctx.literal()) {
                elems.add(visitLiteral(lc));
            }
            return new ListLiteralNode(range(ctx), elems);
        }

        @Override
        public MapLiteralNode visitMapLiteral(SqlStreamParser.MapLiteralContext ctx) {
            TypedList<MapEntryLiteralNode> entries = new TypedList<>(MapEntryLiteralNode.class);
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
            TypedList<StructFieldLiteralNode> fields = new TypedList<>(StructFieldLiteralNode.class);
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

        private Expr fold(TypedList<Expr> xs, InfixOp op) {
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