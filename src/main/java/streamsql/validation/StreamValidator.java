package streamsql.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import streamsql.Catalog;
import streamsql.ast.CreateStream;
import streamsql.ast.Field;
import streamsql.ast.Identifier;
import streamsql.ast.StreamInlineT;
import streamsql.ast.StreamReferenceT;
import streamsql.ast.StreamType;
import streamsql.ast.StructT;

public final class StreamValidator {
    private StreamValidator() {}

    public static void validate(CreateStream stmt, Catalog catalog) {

        var stream = stmt.stream();

        for (StreamType def : stream.types()) {
            List<Identifier> keys = def.distributionKeys();
            if (keys.isEmpty())
                continue; // optional

            // Uniqueness
            Set<Identifier> seen = new HashSet<>();
            for (Identifier k : keys) {
                if (!seen.add(k)) {
                    throw new IllegalArgumentException(
                            "Duplicate field '" + k + "' in DISTRIBUTE clause for stream " +
                                    stream.qName().fullName() + " type alias '" +
                                    (def instanceof StreamInlineT it ? it.alias()
                                            : ((StreamReferenceT) def).alias())
                                    + "'.");
                }
            }

            // Field existence
            Set<Identifier> available;
            if (def instanceof StreamInlineT it) {
                available = it.fields().stream()
                        .map(Field::name)
                        .collect(HashSet::new, HashSet::add, HashSet::addAll);
            } else {
                var rt = (StreamReferenceT) def;
                // referenced type must be a struct
                Optional<StructT> structOpt = catalog.getStruct(rt.ref().qName());
                if (structOpt.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Referenced type '" + rt.ref().qName().fullName() +
                                    "' not found for DISTRIBUTE clause in stream " +
                                    stream.qName().fullName());
                }
                StructT struct = structOpt.get();
                available = struct.fields().stream()
                        .map(Field::name)
                        .collect(HashSet::new, HashSet::add, HashSet::addAll);
            }

            for (Identifier k : keys) {
                if (!available.contains(k)) {
                    throw new IllegalArgumentException(
                            "Field '" + k + "' not found in type for DISTRIBUTE clause (stream " +
                                    stream.qName().fullName() + ").");
                }
            }
        }
    }
}
