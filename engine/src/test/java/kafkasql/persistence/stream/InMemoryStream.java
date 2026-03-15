package kafkasql.persistence.stream;

import kafkasql.runtime.stream.StreamReader;
import kafkasql.runtime.stream.StreamWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory stream backed by a list.
 * <p>
 * Provides both {@link StreamWriter} and {@link StreamReader} views
 * over the same underlying message buffer. Useful for testing the
 * persistence layer without Kafka infrastructure.
 *
 * <h3>Usage</h3>
 * <pre>
 * var stream = new InMemoryStream&lt;SymbolEventLog&gt;("SymbolEventLog");
 * var writer = stream.writer();
 * var reader = stream.reader();
 *
 * writer.write(event);
 * writer.flush();
 *
 * var event = reader.read();  // returns the written event
 * var end = reader.read();    // returns null (no more events)
 * </pre>
 *
 * @param <T> The message type
 */
public class InMemoryStream<T> {

    private final String name;
    private final List<T> messages = new ArrayList<>();

    public InMemoryStream(String name) {
        this.name = name;
    }

    /**
     * Returns an unmodifiable view of all messages in the stream.
     */
    public List<T> messages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Returns the number of messages in the stream.
     */
    public int size() {
        return messages.size();
    }

    /**
     * Creates a writer that appends to this stream.
     */
    public StreamWriter<T> writer() {
        return new StreamWriter<>() {
            @Override
            public String streamName() {
                return name;
            }

            @Override
            public void write(T message) {
                messages.add(message);
            }
        };
    }

    /**
     * Creates a reader that reads from the beginning of this stream.
     * Each reader maintains its own independent cursor position.
     */
    public StreamReader<T> reader() {
        return new StreamReader<>() {
            private int cursor = 0;

            @Override
            public String streamName() {
                return name;
            }

            @Override
            public T read() {
                if (cursor < messages.size()) {
                    return messages.get(cursor++);
                }
                return null;
            }
        };
    }
}
