package com.studioquack.nodelibrary.nodes.datastore;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads a JDBC driver that this library does not ship. The Oracle driver is a closed-source binary
 * under Oracle's own terms, so it is never bundled into the Apache-2.0 image: the operator obtains
 * it, accepts those terms, and places the jar in {@code SQ_JDBC_DRIVERS_DIR} (default
 * {@code /opt/sq/jdbc-drivers}).
 *
 * <p>Only that operator-controlled directory is searched — never a path a flow can write to — and
 * each driver is loaded once and cached per class name for the life of the process.
 */
final class ExternalJdbcDriver {

    static final String DIR_ENV = "SQ_JDBC_DRIVERS_DIR";
    static final Path DEFAULT_DIR = Path.of("/opt/sq/jdbc-drivers");

    private static final Map<String, Driver> LOADED = new ConcurrentHashMap<>();

    private ExternalJdbcDriver() {
    }

    /** The cached driver for {@code className}, loaded from {@link #driversDir()} on first use. */
    static Driver load(String className, String product) throws SQLException {
        Driver driver = LOADED.get(className);
        if (driver != null) {
            return driver;
        }
        synchronized (LOADED) {
            driver = LOADED.get(className);
            if (driver == null) {
                driver = instantiate(className, product, driversDir());
                LOADED.put(className, driver);
            }
            return driver;
        }
    }

    static Path driversDir() {
        String configured = System.getenv(DIR_ENV);
        return configured == null || configured.isBlank() ? DEFAULT_DIR : Path.of(configured.trim());
    }

    static Driver instantiate(String className, String product, Path dir) throws SQLException {
        List<URL> jars = jarsIn(dir);
        if (!jars.isEmpty()) {
            URLClassLoader loader = new URLClassLoader(jars.toArray(URL[]::new),
                    ExternalJdbcDriver.class.getClassLoader());
            try {
                return (Driver) Class.forName(className, true, loader).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
                closeQuietly(loader);
            }
        }
        throw new SQLException(product + " JDBC driver not installed (not bundled with this library): "
                + "obtain it under its vendor's license and place the jar in " + dir, "08001");
    }

    private static List<URL> jarsIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<URL> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                jars.add(jar.toUri().toURL());
            }
        } catch (IOException e) {
            return List.of();
        }
        return jars;
    }

    private static void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
            // nothing was loaded from it; the not-installed error below is what the caller needs
        }
    }
}
