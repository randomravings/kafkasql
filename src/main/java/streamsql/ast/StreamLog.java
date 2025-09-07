package streamsql.ast;

import java.util.List;

public final record StreamLog(QName qName, List<StreamType> types) implements DataStream {}
