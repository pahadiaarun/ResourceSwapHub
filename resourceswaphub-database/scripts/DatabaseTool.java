import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Small JDBC helper for environments without the PostgreSQL client tools.
 * Usage:
 *   java -cp postgresql.jar DatabaseTool.java bootstrap
 *   java -cp postgresql.jar DatabaseTool.java run <file.sql> [<file.sql> ...]
 *   java -cp postgresql.jar DatabaseTool.java execute "<sql>"
 *   java -cp postgresql.jar DatabaseTool.java query "<select sql>"
 */
public class DatabaseTool {
    private static final String HOST = env("BOOKBRIDGE_DB_HOST", "127.0.0.1");
    private static final String PORT = env("BOOKBRIDGE_DB_PORT", "55432");
    private static final String DATABASE = env("BOOKBRIDGE_DB_NAME", "bookbridge");
    private static final String USER = env("BOOKBRIDGE_DB_USER", "bookbridge");
    private static final String PASSWORD = env("BOOKBRIDGE_DB_PASSWORD", "bookbridge_dev");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected 'bootstrap' or 'run <file.sql>'");
        }
        switch (args[0]) {
            case "bootstrap" -> bootstrap();
            case "run" -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Expected at least one SQL file after 'run'");
                }
                try (Connection connection = open(DATABASE)) {
                    for (int i = 1; i < args.length; i++) {
                        Path file = Path.of(args[i]);
                        String sql = Files.readString(file);
                        try (Statement statement = connection.createStatement()) {
                            statement.execute(sql);
                        }
                        System.out.println("Applied " + file);
                    }
                }
            }
            case "execute" -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Expected SQL after 'execute'");
                }
                try (Connection connection = open(DATABASE);
                     Statement statement = connection.createStatement()) {
                    statement.execute(args[1]);
                    System.out.println("Executed: " + args[1]);
                }
            }
            case "query" -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Expected SQL after 'query'");
                }
                try (Connection connection = open(DATABASE);
                     Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery(args[1])) {
                    int columns = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 1; i <= columns; i++) {
                            if (i > 1) line.append(" | ");
                            line.append(rs.getString(i));
                        }
                        System.out.println(line);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    private static void bootstrap() throws Exception {
        if (!USER.matches("[a-zA-Z_][a-zA-Z0-9_]*") || !DATABASE.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Database identifiers must be simple SQL identifiers");
        }
        try (Connection connection = open("postgres");
             Statement statement = connection.createStatement()) {
            String escapedPassword = PASSWORD.replace("'", "''");
            statement.execute("ALTER ROLE \"" + USER + "\" PASSWORD '" + escapedPassword + "'");
            ensureDatabase(statement, DATABASE);
            ensureDatabase(statement, DATABASE + "_test");
            System.out.println("Database " + DATABASE + " and " + DATABASE + "_test are ready.");
        }
    }

    private static void ensureDatabase(Statement statement, String name) throws Exception {
        try (ResultSet rs = statement.executeQuery(
                "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
            if (!rs.next()) {
                statement.execute("CREATE DATABASE \"" + name + "\" OWNER \"" + USER + "\"");
            }
        }
    }

    private static Connection open(String database) throws Exception {
        String url = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + database;
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
