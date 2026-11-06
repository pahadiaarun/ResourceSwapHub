import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * READ-ONLY production audit helper.
 *
 * <p>Connects with the production JDBC URL already configured in
 * resourceswaphub-backend/.env. The connection is forced read-only and every
 * statement is executed inside a READ ONLY transaction, so an accidental write
 * fails instead of mutating production.
 *
 * <p>Credentials are never printed. Use:
 *   java -cp resourceswaphub-database/.local/postgresql-42.7.14.jar \
 *        resourceswaphub-database/scripts/tools/ProdAudit.java <command> [args]
 */
public class ProdAudit {

    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv(Path.of("resourceswaphub-backend/.env"));
        String url = env.get("BOOKBRIDGE_DB_URL");
        String user = env.get("BOOKBRIDGE_DB_USER");
        String pass = env.get("BOOKBRIDGE_DB_PASSWORD");
        if (url == null || user == null || pass == null) {
            throw new IllegalStateException("Production DB settings not found in .env");
        }
        if (!url.contains("aivencloud.com")) {
            throw new IllegalStateException("Refusing to run: target is not the Aiven production host");
        }

        String command = args.length == 0 ? "identity" : args[0];
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);
            c.setReadOnly(true);
            try (Statement st = c.createStatement()) {
                st.execute("SET TRANSACTION READ ONLY");
            }
            switch (command) {
                case "identity" -> identity(c);
                case "tables" -> tables(c);
                case "query" -> {
                    if (args.length < 2) throw new IllegalArgumentException("query needs SQL");
                    query(c, args[1]);
                }
                case "file" -> {
                    if (args.length < 2) throw new IllegalArgumentException("file needs a path");
                    String sql = Files.readString(Path.of(args[1]));
                    for (String part : sql.split(";\\s*\\n")) {
                        String s = part.trim();
                        if (s.isEmpty() || s.startsWith("--")) continue;
                        query(c, s);
                    }
                }
                case "schema" -> schema(c);
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
            c.rollback();
        }
    }

    static Map<String, String> loadEnv(Path file) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (val.length() >= 2 && (val.startsWith("\"") && val.endsWith("\""))) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    static void identity(Connection c) throws Exception {
        System.out.println("current_database: " + one(c, "SELECT current_database()"));
        System.out.println("current_user: " + one(c, "SELECT current_user"));
        System.out.println("server_addr: " + one(c, "SELECT coalesce(inet_server_addr()::text,'(unreported)')"));
        System.out.println("server_port: " + one(c, "SELECT coalesce(inet_server_port()::text,'(unreported)')"));
        System.out.println("version: " + one(c, "SELECT version()"));
        System.out.println("ssl: " + one(c, "SELECT coalesce((SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid())::text,'unknown')"));
        System.out.println("flyway_version: " + one(c, "SELECT coalesce(max(version),'(none)') FROM flyway_schema_history WHERE success"));
        System.out.println("flyway_latest_installed_on: " + one(c, "SELECT coalesce(max(installed_on)::text,'(none)') FROM flyway_schema_history WHERE success"));
        System.out.println("txn_read_only: " + one(c, "SELECT current_setting('transaction_read_only')"));
        System.out.println("now_utc: " + one(c, "SELECT now() AT TIME ZONE 'UTC'"));
    }

    static void tables(Connection c) throws Exception {
        query(c, """
            SELECT c.relname AS table_name,
                   c.reltuples::bigint AS approx_rows,
                   pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind = 'r'
            ORDER BY c.relname
            """);
    }

    static void schema(Connection c) throws Exception {
        System.out.println("== FOREIGN KEYS ==");
        query(c, """
            SELECT tc.table_name AS child,
                   kcu.column_name AS child_col,
                   ccu.table_name AS parent,
                   ccu.column_name AS parent_col,
                   rc.delete_rule AS on_delete,
                   rc.update_rule AS on_update
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON kcu.constraint_name = tc.constraint_name AND kcu.constraint_schema = tc.constraint_schema
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name AND ccu.constraint_schema = tc.constraint_schema
            JOIN information_schema.referential_constraints rc
              ON rc.constraint_name = tc.constraint_name AND rc.constraint_schema = tc.constraint_schema
            WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
            ORDER BY tc.table_name, kcu.column_name
            """);
        System.out.println();
        System.out.println("== TABLES WITHOUT FK INBOUND/OUTBOUND ==");
        query(c, """
            SELECT c.relname
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname='public' AND c.relkind='r'
              AND NOT EXISTS (
                SELECT 1 FROM pg_constraint p
                WHERE p.contype='f' AND (p.conrelid=c.oid OR p.confrelid=c.oid))
            ORDER BY 1
            """);
        System.out.println();
        System.out.println("== TRIGGERS ==");
        query(c, """
            SELECT event_object_table AS table_name, trigger_name, action_timing, event_manipulation
            FROM information_schema.triggers
            WHERE trigger_schema='public'
            ORDER BY 1,2
            """);
        System.out.println();
        System.out.println("== VIEWS ==");
        query(c, """
            SELECT table_name FROM information_schema.views WHERE table_schema='public' ORDER BY 1
            """);
    }

    static void query(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<String[]> rows = new ArrayList<>();
            while (rs.next()) {
                String[] row = new String[n];
                for (int i = 1; i <= n; i++) row[i - 1] = rs.getString(i);
                rows.add(row);
            }
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (i > 1) header.append(" | ");
                header.append(md.getColumnLabel(i));
            }
            System.out.println(header);
            System.out.println("-".repeat(Math.min(header.length(), 120)));
            for (String[] row : rows) System.out.println(String.join(" | ", row));
            System.out.println("(" + rows.size() + " rows)");
        }
    }

    static String one(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "(no row)";
        }
    }
}
