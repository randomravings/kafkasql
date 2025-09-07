package streamsql.ast;

import java.util.List;

public final record StreamCompact(QName qName, List<StreamType> types) implements DataStream {}
