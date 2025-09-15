package streamsql.ast;

public sealed interface CreateType extends CreateStmt permits CreateScalar, CreateEnum, CreateStruct, CreateUnion {
    public QName qName();
    public ComplexT type();
}
