package kafkasql.lang.ast;

public class StreamTypeList extends AstListNode<StreamType> {
  public StreamTypeList(Range range) {
    super(range);
  }
  public StreamTypeList(Range range, java.util.List<StreamType> items) {
    super(range, items);
  }
}
