package streamsql.ast;

public final class ProjectionAll implements Projection {
    private static final ProjectionAll INSTANCE = new ProjectionAll();
    private ProjectionAll() {}
    public static ProjectionAll getInstance() {
        return INSTANCE;
    }
}
