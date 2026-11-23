package com.studioquack.nodelibrary.nodes.datastore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalJdbcDriverTest {

    @Test
    void missingDriverNamesTheDirectoryToInstallInto(@TempDir Path dir) {
        SQLException e = assertThrows(SQLException.class,
                () -> ExternalJdbcDriver.instantiate("oracle.jdbc.OracleDriver", "Oracle", dir));
        assertEquals("08001", e.getSQLState());
        assertTrue(e.getMessage().startsWith("Oracle JDBC driver not installed"), e.getMessage());
        assertTrue(e.getMessage().contains(dir.toString()), e.getMessage());
    }

    @Test
    void jarWithoutTheDriverClassIsTreatedAsNotInstalled(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("unrelated.jar"), new byte[0]);
        assertThrows(SQLException.class,
                () -> ExternalJdbcDriver.instantiate("oracle.jdbc.OracleDriver", "Oracle", dir));
    }

    @Test
    void absentDirectoryIsNotInstalled(@TempDir Path dir) {
        assertThrows(SQLException.class,
                () -> ExternalJdbcDriver.instantiate("oracle.jdbc.OracleDriver", "Oracle", dir.resolve("missing")));
    }
}
