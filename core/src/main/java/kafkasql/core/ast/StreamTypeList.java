package kafkasql.core.ast;

import kafkasql.core.Range;

public class StreamTypeList extends AstListNode<StreamType> {
  public StreamTypeList(Range range) {
    super(range);
  }
  public StreamTypeList(Range range, java.util.List<StreamType> items) {
    super(range, items);
  }    
}
