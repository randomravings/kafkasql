package kafkasql.core.ast;

import java.util.List;

public class UnionMemberList extends AstListNode<UnionMember> {
  public UnionMemberList(Range range) {
    super(range);
  }
  public UnionMemberList(Range range, List<UnionMember> items) {
    super(range, items);
  }
}
