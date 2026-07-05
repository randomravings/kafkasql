package kafkasql.lang;

import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.SemanticModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CursorSemanticTest {

    @Test
    void readFromUnknownCursorShouldFail() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);

                                                READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;
            """;

        var model = compile(source);
                assertTrue(model.diags().hasError(), "Unknown cursor must fail semantic validation");
                assertTrue(model.diags().all().toString().contains("Unknown cursor: 'test.orders-cg'"));
    }

    @Test
    void readBeforeCursorDeclarationShouldFail() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);

                        READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;

                        CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders);
            """;

        var model = compile(source);
        assertTrue(model.diags().hasError(), "Read must require prior cursor declaration");
        assertTrue(model.diags().all().toString().contains("Unknown cursor: 'test.orders-cg'"));
    }

    @Test
    void readFromCursorWithoutAssignedStreamShouldFail() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);
            CREATE STREAM Payments (TYPE Row AS test.Rec);

                        CREATE CURSOR 'orders-cg' FOR STREAMS (test.Payments);

                        READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;
            """;

        var model = compile(source);
        assertTrue(model.diags().hasError(), "Read must require stream assignment in the cursor");
        assertTrue(model.diags().all().toString().contains("is not assigned to stream 'test.Orders'"));
    }

    @Test
    void readFromDeclaredCursorWithAssignedStreamShouldPass() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);

                                                CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders);

                                                READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(),
                        "Declared cursor with assigned stream should pass: " + model.diags().all());
    }

    @Test
        void sameCursorNameInDifferentContextsShouldPass() {
        String source = """
            CREATE CONTEXT sales;
            CREATE CONTEXT billing;

            USE CONTEXT sales;
            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS sales.Rec);
            CREATE CURSOR 'shared-cg' FOR STREAMS (sales.Orders);

            USE CONTEXT billing;
            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Invoices (TYPE Row AS billing.Rec);
                                                CREATE CURSOR 'shared-cg' FOR STREAMS (billing.Invoices);

                                                READ FROM billing.Invoices FROM CURSOR 'billing.shared-cg'
              TYPE Row *;
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(),
                        "Same cursor name should be allowed across contexts: " + model.diags().all());
    }

    @Test
        void cursorCannotIncludeStreamFromOtherContext() {
        String source = """
            CREATE CONTEXT sales;
            CREATE CONTEXT billing;

            USE CONTEXT sales;
            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS sales.Rec);

            USE CONTEXT billing;
            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Invoices (TYPE Row AS billing.Rec);

            CREATE CURSOR 'bad-cg' FOR STREAMS (sales.Orders);
            """;

        var model = compile(source);
        assertTrue(model.diags().hasError(), "Cross-context stream mapping must fail");
        assertTrue(model.diags().all().toString().contains("cannot include stream from context"));
    }

    @Test
    void alterCursorResetStreamShouldPass() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);

            CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders RESET LATEST);
                        ALTER CURSOR 'orders-cg' RESET STREAM test.Orders TO BEGINNING;

                        READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(),
                        "Resetting an existing cursor stream policy should pass: " + model.diags().all());
        }

        @Test
        void alterCursorSeekStreamShouldPass() {
                String source = """
                        CREATE CONTEXT test;
                        USE CONTEXT test;

                        CREATE TYPE Rec AS STRUCT (Id INT32);
                        CREATE STREAM Orders (TYPE Row AS test.Rec);

                        CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders RESET LATEST);
                        ALTER CURSOR 'orders-cg' SEEK STREAM test.Orders TO (
                            0: BEGINNING,
                            1: END,
                            3: 1001,
                            4: '2026-07-01T00:00:00.001Z'
                        );
                        """;

                var model = compile(source);
                assertFalse(model.diags().hasError(),
                        "Seeking cursor partitions should pass semantic validation: " + model.diags().all());
        }

        @Test
        void alterCursorSeekStreamWithDuplicatePartitionShouldFail() {
                String source = """
                        CREATE CONTEXT test;
                        USE CONTEXT test;

                        CREATE TYPE Rec AS STRUCT (Id INT32);
                        CREATE STREAM Orders (TYPE Row AS test.Rec);

                        CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders);
                        ALTER CURSOR 'orders-cg' SEEK STREAM test.Orders TO (
                            0: BEGINNING,
                            0: END
                        );
                        """;

                var model = compile(source);
                assertTrue(model.diags().hasError(), "Duplicate partition should fail semantic validation");
                assertTrue(model.diags().all().toString().contains("Duplicate partition in cursor seek list"));
    }

    @Test
    void dropCursorShouldAllowRecreate() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;

            CREATE TYPE Rec AS STRUCT (Id INT32);
            CREATE STREAM Orders (TYPE Row AS test.Rec);

            CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders);
            DROP CURSOR 'orders-cg';
            CREATE CURSOR 'orders-cg' FOR STREAMS (test.Orders RESET EARLIEST);
                        READ FROM test.Orders FROM CURSOR 'test.orders-cg'
              TYPE Row *;
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(),
            "Dropped cursor name should be reusable: " + model.diags().all());
    }

    private SemanticModel compile(String source) {
        Input input = new StringInput("CursorSemanticTest.kafka", source);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(input), args);
        return KafkaSqlParser.bind(parseResult);
    }
}
