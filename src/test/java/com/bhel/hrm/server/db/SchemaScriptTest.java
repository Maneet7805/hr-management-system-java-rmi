package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests of reading the schema script. No database is needed. */
class SchemaScriptTest {

    @Test
    void splitsStatementsAtSemicolons() {
        List<String> statements = SchemaInstaller.statements("CREATE TABLE a (x INT);\nCREATE TABLE b (y INT);\n");

        assertEquals(List.of("CREATE TABLE a (x INT)", "CREATE TABLE b (y INT)"), statements);
    }

    @Test
    void ignoresCommentLinesAndBlankLines() {
        String script = "-- a comment\n\nCREATE TABLE a (x INT);\n   \n-- another\nCREATE TABLE b (y INT);";

        assertEquals(2, SchemaInstaller.statements(script).size());
    }

    @Test
    void keepsAStatementThatSpansSeveralLinesAsOne() {
        String script = "CREATE TABLE a (\n  x INT,\n  y INT\n);";

        List<String> statements = SchemaInstaller.statements(script);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("x INT,"));
        assertTrue(statements.get(0).contains("y INT"));
    }

    @Test
    void keepsALastStatementThatHasNoSemicolon() {
        assertEquals(List.of("SELECT 1"), SchemaInstaller.statements("SELECT 1"));
    }

    @Test
    void bundledScriptCreatesTheFourTablesAndSeedsTheLeaveTypes() {
        List<String> statements = SchemaInstaller.bundledStatements();

        long tables = statements.stream().filter(s -> s.startsWith("CREATE TABLE IF NOT EXISTS")).count();
        long seeds = statements.stream().filter(s -> s.startsWith("INSERT IGNORE INTO leave_type")).count();

        assertEquals(4, tables);
        assertEquals(1, seeds);
    }
}
