package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.runtime.diagnostics.Range;

public sealed interface AlterStmt extends Stmt {

    QName target();

    // ── ALTER TYPE actions ──

    sealed interface AlterTypeAction extends AstNode
        permits AddField, AddSymbol, DropMember {}

    record AlterType(Range range, QName target, AlterTypeAction action) implements AlterStmt {}

    record AddField(Range range, StructFieldDecl field) implements AlterTypeAction {}
    record AddSymbol(Range range, EnumSymbolDecl symbol) implements AlterTypeAction {}
    record DropMember(Range range, Identifier name) implements AlterTypeAction {}

    // ── ALTER STREAM actions ──

    sealed interface AlterStreamAction extends AstNode
        permits AddStreamType, DropStreamType {}

    record AlterStream(Range range, QName target, AlterStreamAction action) implements AlterStmt {}

    record AddStreamType(Range range, StreamMemberDecl member) implements AlterStreamAction {}
    record DropStreamType(Range range, Identifier name) implements AlterStreamAction {}
}
