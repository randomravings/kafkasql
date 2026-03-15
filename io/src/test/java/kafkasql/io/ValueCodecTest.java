package kafkasql.io;

import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;
import kafkasql.runtime.value.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for ValueCodec: encode → decode for every Value type.
 */
class ValueCodecTest {

    // ========================================================================
    // Scalar round-trips
    // ========================================================================

    @Test
    void scalarInt32_roundTrip() throws Exception {
        var type = new ScalarType(Name.of("test", "Count"), PrimitiveType.int32(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var value = new ScalarValue(type, 42);

        var decoded = roundTrip(value, type);

        assertInstanceOf(ScalarValue.class, decoded);
        assertEquals(42, ((ScalarValue) decoded).value());
    }

    @Test
    void scalarString_roundTrip() throws Exception {
        var type = new ScalarType(Name.of("PersonId"), PrimitiveType.string(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var value = new ScalarValue(type, "abc-123");

        var decoded = roundTrip(value, type);

        assertInstanceOf(ScalarValue.class, decoded);
        assertEquals("abc-123", ((ScalarValue) decoded).value());
    }

    @Test
    void scalarUuid_roundTrip() throws Exception {
        var type = new ScalarType(Name.of("EventId"), PrimitiveType.uuid(),
                Optional.empty(), Optional.empty(), Optional.empty());
        UUID id = UUID.randomUUID();
        var value = new ScalarValue(type, id);

        var decoded = roundTrip(value, type);

        assertInstanceOf(ScalarValue.class, decoded);
        assertEquals(id, ((ScalarValue) decoded).value());
    }

    // ========================================================================
    // Enum round-trips
    // ========================================================================

    @Test
    void enum_roundTrip() throws Exception {
        var type = buildStatusEnum();
        var symbol = type.symbols().get(1); // ACTIVE = 1
        var value = new EnumValue(type, symbol);

        var decoded = roundTrip(value, type);

        assertInstanceOf(EnumValue.class, decoded);
        var ev = (EnumValue) decoded;
        assertEquals("ACTIVE", ev.symbolName());
        assertEquals(1, ev.numericValue());
    }

    // ========================================================================
    // Struct round-trips
    // ========================================================================

    @Test
    void structWithPrimitives_roundTrip() throws Exception {
        var type = buildPersonStruct();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("Id", 42);
        fields.put("Name", "Alice");
        fields.put("Active", true);
        var value = new StructValue(type, fields);

        var decoded = roundTrip(value, type);

        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals(42, sv.get("Id"));
        assertEquals("Alice", sv.get("Name"));
        assertEquals(true, sv.get("Active"));
    }

    @Test
    void structWithNullableField_roundTrip() throws Exception {
        var type = buildAddressStruct();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("Street", "123 Main St");
        fields.put("Zip", null); // nullable
        var value = new StructValue(type, fields);

        var decoded = roundTrip(value, type);

        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals("123 Main St", sv.get("Street"));
        assertNull(sv.get("Zip"));
    }

    @Test
    void structWithNullableField_nonNull_roundTrip() throws Exception {
        var type = buildAddressStruct();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("Street", "123 Main St");
        fields.put("Zip", "90210");
        var value = new StructValue(type, fields);

        var decoded = roundTrip(value, type);

        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals("123 Main St", sv.get("Street"));
        assertEquals("90210", sv.get("Zip"));
    }

    @Test
    void structWithNestedEnum_roundTrip() throws Exception {
        var statusEnum = buildStatusEnum();
        var structFields = new LinkedHashMap<String, StructTypeField>();
        structFields.put("Name", new StructTypeField("Name", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        structFields.put("Status", new StructTypeField("Status", statusEnum, false, Optional.empty(), Optional.empty()));
        var type = new StructType(Name.of("test", "Person"), structFields, List.of(), Optional.empty());

        var fields = new LinkedHashMap<String, Object>();
        fields.put("Name", "Bob");
        fields.put("Status", new EnumValue(statusEnum, statusEnum.symbols().get(1))); // ACTIVE
        var value = new StructValue(type, fields);

        var decoded = roundTrip(value, type);

        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals("Bob", sv.get("Name"));
        assertInstanceOf(EnumValue.class, sv.get("Status"));
        assertEquals("ACTIVE", ((EnumValue) sv.get("Status")).symbolName());
    }

    @Test
    void structWithNestedScalar_roundTrip() throws Exception {
        var scalarType = new ScalarType(Name.of("test", "Money"), PrimitiveType.int64(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var structFields = new LinkedHashMap<String, StructTypeField>();
        structFields.put("Label", new StructTypeField("Label", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        structFields.put("Amount", new StructTypeField("Amount", scalarType, false, Optional.empty(), Optional.empty()));
        var type = new StructType(Name.of("test", "Payment"), structFields, List.of(), Optional.empty());

        var fields = new LinkedHashMap<String, Object>();
        fields.put("Label", "Invoice #1");
        fields.put("Amount", new ScalarValue(scalarType, 5000L));
        var value = new StructValue(type, fields);

        var decoded = roundTrip(value, type);

        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals("Invoice #1", sv.get("Label"));
        assertInstanceOf(ScalarValue.class, sv.get("Amount"));
        assertEquals(5000L, ((ScalarValue) sv.get("Amount")).value());
    }

    // ========================================================================
    // Union round-trips
    // ========================================================================

    @Test
    void unionWithPrimitiveMember_roundTrip() throws Exception {
        var members = new LinkedHashMap<String, UnionTypeMember>();
        members.put("Id", new UnionTypeMember("Id", PrimitiveType.int32(), Optional.empty()));
        members.put("Name", new UnionTypeMember("Name", PrimitiveType.string(), Optional.empty()));
        var type = new UnionType(Name.of("test", "IdOrName"), members, Optional.empty());

        // Union with string member
        var value = new UnionValue(type, "Name", "Alice");

        var decoded = roundTrip(value, type);

        assertInstanceOf(UnionValue.class, decoded);
        var uv = (UnionValue) decoded;
        assertEquals("Name", uv.memberName());
        assertEquals("Alice", uv.value());
    }

    @Test
    void unionWithEnumMember_roundTrip() throws Exception {
        var statusEnum = buildStatusEnum();
        var members = new LinkedHashMap<String, UnionTypeMember>();
        members.put("StatusVal", new UnionTypeMember("StatusVal", statusEnum, Optional.empty()));
        members.put("Code", new UnionTypeMember("Code", PrimitiveType.int32(), Optional.empty()));
        var type = new UnionType(Name.of("test", "StatusOrCode"), members, Optional.empty());

        var enumVal = new EnumValue(statusEnum, statusEnum.symbols().get(2)); // DISABLED
        var value = new UnionValue(type, "StatusVal", enumVal);

        var decoded = roundTrip(value, type);

        assertInstanceOf(UnionValue.class, decoded);
        var uv = (UnionValue) decoded;
        assertEquals("StatusVal", uv.memberName());
        assertInstanceOf(EnumValue.class, uv.value());
        assertEquals("DISABLED", ((EnumValue) uv.value()).symbolName());
    }

    // ========================================================================
    // Primitive exhaustive round-trips
    // ========================================================================

    @Test
    void allPrimitives_roundTrip() throws Exception {
        assertPrimitiveRoundTrip(PrimitiveType.bool(), true);
        assertPrimitiveRoundTrip(PrimitiveType.bool(), false);
        assertPrimitiveRoundTrip(PrimitiveType.int8(), (byte) 127);
        assertPrimitiveRoundTrip(PrimitiveType.int16(), (short) 32000);
        assertPrimitiveRoundTrip(PrimitiveType.int32(), 42);
        assertPrimitiveRoundTrip(PrimitiveType.int64(), Long.MAX_VALUE);
        assertPrimitiveRoundTrip(PrimitiveType.float32(), 3.14f);
        assertPrimitiveRoundTrip(PrimitiveType.float64(), 2.718281828);
        assertPrimitiveRoundTrip(PrimitiveType.string(), "hello world");
        assertPrimitiveRoundTrip(PrimitiveType.bytes(), new byte[]{1, 2, 3, 0, -1});
        assertPrimitiveRoundTrip(PrimitiveType.uuid(), UUID.randomUUID());
        assertPrimitiveRoundTrip(PrimitiveType.decimal((byte) 10, (byte) 0), new BigDecimal(12345));
    }

    @Test
    void temporalPrimitives_roundTrip() throws Exception {
        assertPrimitiveRoundTrip(PrimitiveType.date(), LocalDate.of(2026, 3, 14));
        assertPrimitiveRoundTrip(PrimitiveType.time((byte) 3), LocalTime.of(14, 30, 15));
        assertPrimitiveRoundTrip(PrimitiveType.timestamp((byte) 3), LocalDateTime.of(2026, 3, 14, 10, 30, 0));
        assertPrimitiveRoundTrip(PrimitiveType.timestampTz((byte) 3),
                ZonedDateTime.of(2026, 3, 14, 10, 30, 0, 0, ZoneId.of("America/New_York")));
    }

    // ========================================================================
    // byte[] round-trip helper (top-level Value encode/decode)
    // ========================================================================

    @Test
    void byteArrayConvenience_roundTrip() throws Exception {
        var type = buildPersonStruct();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("Id", 99);
        fields.put("Name", "Charlie");
        fields.put("Active", false);
        var value = new StructValue(type, fields);

        byte[] bytes = ValueCodec.toByteArray(value);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        var decoded = ValueCodec.fromByteArray(type, bytes);
        assertInstanceOf(StructValue.class, decoded);
        var sv = (StructValue) decoded;
        assertEquals(99, sv.get("Id"));
        assertEquals("Charlie", sv.get("Name"));
        assertEquals(false, sv.get("Active"));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Value roundTrip(Value value, AnyType type) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueCodec.encode(value, baos);
        return ValueCodec.decode(type, new ByteArrayInputStream(baos.toByteArray()));
    }

    private void assertPrimitiveRoundTrip(PrimitiveType type, Object value) throws Exception {
        // Wrap in a single-field struct to test field-level encode/decode
        var structFields = new LinkedHashMap<String, StructTypeField>();
        structFields.put("v", new StructTypeField("v", type, false, Optional.empty(), Optional.empty()));
        var structType = new StructType(Name.of("Wrapper"), structFields, List.of(), Optional.empty());

        var fields = new LinkedHashMap<String, Object>();
        fields.put("v", value);
        var sv = new StructValue(structType, fields);

        var decoded = (StructValue) roundTrip(sv, structType);

        if (value instanceof byte[] expected) {
            assertArrayEquals(expected, (byte[]) decoded.get("v"));
        } else {
            assertEquals(value, decoded.get("v"));
        }
    }

    private EnumType buildStatusEnum() {
        return new EnumType(
                Name.of("test", "Status"),
                PrimitiveType.int32(),
                List.of(
                        new EnumTypeSymbol("PENDING", 0, Optional.empty()),
                        new EnumTypeSymbol("ACTIVE", 1, Optional.empty()),
                        new EnumTypeSymbol("DISABLED", 2, Optional.empty())
                ),
                Optional.empty()
        );
    }

    private StructType buildPersonStruct() {
        var fields = new LinkedHashMap<String, StructTypeField>();
        fields.put("Id", new StructTypeField("Id", PrimitiveType.int32(), false, Optional.empty(), Optional.empty()));
        fields.put("Name", new StructTypeField("Name", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        fields.put("Active", new StructTypeField("Active", PrimitiveType.bool(), false, Optional.empty(), Optional.empty()));
        return new StructType(Name.of("test", "Person"), fields, List.of(), Optional.empty());
    }

    private StructType buildAddressStruct() {
        var fields = new LinkedHashMap<String, StructTypeField>();
        fields.put("Street", new StructTypeField("Street", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        fields.put("Zip", new StructTypeField("Zip", PrimitiveType.string(), true, Optional.empty(), Optional.empty()));
        return new StructType(Name.of("test", "Address"), fields, List.of(), Optional.empty());
    }
}
