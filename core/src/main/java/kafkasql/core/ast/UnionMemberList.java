package kafkasql.core.ast;

import java.util.List;

import kafkasql.core.Range;

public class UnionMemberList extends AstListNode<UnionMember> {
  public UnionMemberList(Range range) {
    super(range);
  }
  public UnionMemberList(Range range, List<UnionMember> items) {
    super(range, items);
  }
}
