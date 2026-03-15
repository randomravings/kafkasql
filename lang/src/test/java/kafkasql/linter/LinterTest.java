package kafkasql.linter;

import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.SemanticModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Linter naming rules")
class LinterTest {
    
    @Test
    @DisplayName("PascalCase type names should pass")
    void pascalCaseTypesShouldPass() {
        var result = lintCode("""
            CREATE TYPE UserStatus AS ENUM (ACTIVE = 1, INACTIVE = 2);
            CREATE TYPE HTTPResponse AS STRUCT (StatusCode INT32);
            """);
        
        assertFalse(result.hasWarning(), "Should not have warnings for PascalCase types");
    }
    
    @Test
    @DisplayName("Non-PascalCase type names should warn")
    void nonPascalCaseTypesShouldWarn() {
        var result = lintCode("""
            CREATE TYPE userStatus AS ENUM (ACTIVE = 1);
            CREATE TYPE user_status AS STRUCT (Id INT32);
            """);
        
        assertTrue(result.hasWarning(), "Should have warnings for non-PascalCase types");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("userStatus") && d.message().contains("PascalCase")),
            "Should warn about 'userStatus'");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("user_status") && d.message().contains("PascalCase")),
            "Should warn about 'user_status'");
    }
    
    @Test
    @DisplayName("SCREAMING_SNAKE_CASE enum symbols should pass")
    void screamingSnakeCaseEnumsShouldPass() {
        var result = lintCode("""
            CREATE TYPE Status AS ENUM (
                ACTIVE = 1,
                INACTIVE = 2,
                PENDING_APPROVAL = 3,
                HTTP_200_OK = 200
            );
            """);
        
        assertFalse(result.hasWarning(), "Should not have warnings for SCREAMING_SNAKE_CASE enums");
    }
    
    @Test
    @DisplayName("Non-SCREAMING_SNAKE_CASE enum symbols should warn")
    void nonScreamingSnakeCaseEnumsShouldWarn() {
        var result = lintCode("""
            CREATE TYPE Status AS ENUM (
                Active = 1,
                inactive = 2,
                PendingApproval = 3
            );
            """);
        
        assertTrue(result.hasWarning(), "Should have warnings for non-SCREAMING_SNAKE_CASE enums");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("Active") && d.message().contains("SCREAMING_SNAKE_CASE")),
            "Should warn about 'Active'");
    }
    
    @Test
    @DisplayName("PascalCase fields should pass")
    void pascalCaseFieldsShouldPass() {
        var result = lintCode("""
            CREATE TYPE User AS STRUCT (
                FirstName STRING,
                LastName STRING,
                UserId INT32
            );
            """);
        
        assertFalse(result.hasWarning(), "Should not have warnings for PascalCase fields");
    }
    
    @Test
    @DisplayName("Non-PascalCase fields should warn")
    void nonPascalCaseFieldsShouldWarn() {
        var result = lintCode("""
            CREATE TYPE User AS STRUCT (
                firstName STRING,
                last_name STRING
            );
            """);
        
        assertTrue(result.hasWarning(), "Should have warnings for non-PascalCase fields");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("firstName") && d.message().contains("PascalCase")),
            "Should warn about 'firstName'");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("last_name") && d.message().contains("PascalCase")),
            "Should warn about 'last_name'");
    }
    
    @Test
    @DisplayName("PascalCase union members should pass")
    void pascalCaseUnionMembersShouldPass() {
        var result = lintCode("""
            CREATE TYPE Value AS UNION (
                IntValue INT32,
                StringValue STRING
            );
            """);
        
        assertFalse(result.hasWarning(), "Should not have warnings for PascalCase union members");
    }
    
    @Test
    @DisplayName("Exact case union member references should pass")
    void exactCaseUnionMemberReferencesShouldPass() {
        var result = lintCode("""
            CREATE TYPE Status AS ENUM (ACTIVE = 1, INACTIVE = 2);
            CREATE TYPE Value AS UNION (
                IntValue INT32,
                StatusValue Status
            );
            CREATE TYPE Container AS STRUCT (
                MyValue Value DEFAULT Value$IntValue(42)
            );
            """);
        
        assertFalse(result.hasWarning(), "Should not have warnings for exact case member references");
    }
    
    @Test
    @DisplayName("Wrong case union member references should warn")
    void wrongCaseUnionMemberReferencesShouldWarn() {
        var result = lintCode("""
            CREATE TYPE Status AS ENUM (ACTIVE = 1, INACTIVE = 2);
            CREATE TYPE Value AS UNION (
                IntValue INT32,
                StatusValue Status
            );
            CREATE TYPE Container AS STRUCT (
                MyValue Value DEFAULT Value$intValue(42)
            );
            """);
        
        assertTrue(result.hasWarning(), "Should have warnings for wrong case member references");
        assertTrue(result.all().stream()
            .anyMatch(d -> d.message().contains("intValue") && d.message().contains("IntValue")),
            "Should warn that 'intValue' should be 'IntValue'");
    }
    
    private Diagnostics lintCode(String code) {
        // Parse and bind
        var source = new StringInput("<test>", code);
        var args = new KafkaSqlArgs(Paths.get("."), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(source), args);
        
        if (parseResult.diags().hasError()) {
            fail("Parse errors: " + parseResult.diags().all());
        }
        
        SemanticModel model = KafkaSqlParser.bind(parseResult);
        
        if (model.diags().hasError()) {
            fail("Semantic errors: " + model.diags().all());
        }
        
        // Lint
        LintEngine linter = new LintEngine();
        Diagnostics lintDiags = linter.lint(parseResult.scripts(), model.symbols(), model.bindings());
        
        // Print all lint diagnostics for debugging
        lintDiags.all().forEach(d -> System.out.println("LINT: " + d));
        
        return lintDiags;
    }
}
