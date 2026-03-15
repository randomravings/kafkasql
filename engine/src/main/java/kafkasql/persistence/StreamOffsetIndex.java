package kafkasql.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Index mapping data-topic partition offsets to schema version boundaries.
 * <p>
 * Built during event log replay (cold start) and updated live during ALTER
 * events. For each stream partition, stores an ordered list of
 * (offset, version) pairs. Given any record offset, the schema version
 * is determined by the highest boundary offset &le; the record offset.
 *
 * <h3>Example</h3>
 * <pre>
 * stream "com.Orders", partition 0:
 *   offset 42 → version 2
 *   offset 98 → version 3
 *
 * Record at offset 50 → version 2
 * Record at offset 100 → version 3
 * Record at offset 10 → version 1 (before any marker)
 * </pre>
 */
public class StreamOffsetIndex {

    /** stream → partition → sorted list of (offset, version) boundaries */
    private final Map<String, Map<Integer, List<OffsetVersion>>> index = new HashMap<>();

    public record OffsetVersion(long offset, int version) {}

    /**
     * Records schema-change marker offsets for a given version.
     *
     * @param streamOffsets Map of stream name → (partition → offset)
     * @param version       The schema version that begins after these offsets
     */
    public void record(Map<String, Map<Integer, Long>> streamOffsets, int version) {
        for (var streamEntry : streamOffsets.entrySet()) {
            var partMap = index.computeIfAbsent(streamEntry.getKey(), k -> new HashMap<>());
            for (var partEntry : streamEntry.getValue().entrySet()) {
                var list = partMap.computeIfAbsent(partEntry.getKey(), k -> new ArrayList<>());
                list.add(new OffsetVersion(partEntry.getValue(), version));
            }
        }
    }

    /**
     * Returns the schema version for a given record offset.
     *
     * @param stream    Stream name
     * @param partition Partition number
     * @param offset    Record offset
     * @return Schema version (1 if before any marker)
     */
    public int versionAt(String stream, int partition, long offset) {
        var partMap = index.get(stream);
        if (partMap == null) return 1;
        var list = partMap.get(partition);
        if (list == null || list.isEmpty()) return 1;
        int version = 1;
        for (var entry : list) {
            if (entry.offset() <= offset) {
                version = entry.version();
            } else {
                break;
            }
        }
        return version;
    }

    /**
     * Returns all boundaries for a given stream partition, for inspection.
     */
    public List<OffsetVersion> boundaries(String stream, int partition) {
        var partMap = index.get(stream);
        if (partMap == null) return List.of();
        return List.copyOf(partMap.getOrDefault(partition, List.of()));
    }

    /**
     * Returns true if the index has any entries.
     */
    public boolean isEmpty() {
        return index.isEmpty();
    }
}
