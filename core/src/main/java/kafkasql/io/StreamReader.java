package kafkasql.io;

import kafkasql.types.StreamType;

public interface StreamReader {
    int readNextTypeId();
    <T extends StreamType<T>> T read(int typeId);
}
