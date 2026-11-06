import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local dry-run harness for the production cleanup script.
 *
 * <p>load - creates a throwaway local database, rebuilds every production table in
 *          schema `public` from the snapshot's column definitions, and loads the TSVs.
 * <p>run  - executes a .sql file against that throwaway database as one simple-query
 *          batch (so DO blocks and multi-statement scripts work).
 * <p>show - prints the table counts of the throwaway database.
 *
 * This never connects to production. Local defaults only.
 */
public class LocalDryRun {

    static final String BASE = "jdbc:postgresql://127.0.0.1:55432/";
    static final String USER = "bookbridge";
    static final String PASS = "bookbridge_dev";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: load|run|show <args>");
        switch (args[0]) {
            case "load" -> load(Path.of(args[1]), args[2]);
            case "run" -> run(args[1], Path.of(args[2]));
            case "show" -> show(args[1]);
            default -> throw new IllegalArgumentException("unknown " + args[0]);
        }
    }

    static void load(Path dir, String db) throws Exception {
        try (Connection admin = DriverManager.getConnection(BASE + "postgres", USER, PASS)) {
            admin.setAutoCommit(true);
            try (Statement st = admin.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
                st.execute("CREATE DATABASE " + db);
            }
        }
        Map<String, Long> rows = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("MANIFEST.tsv"))) {
            if (line.startsWith("#") || line.isBlank()) continue;
            String[] p = line.split("\t");
            order.add(p[0]);
            rows.put(p[0], Long.parseLong(p[1]));
        }
        try (Connection c = DriverManager.getConnection(BASE + db, USER, PASS)) {
            c.setAutoCommit(false);
            // Replay production column definitions captured during the dump.
            List<String> ddl = Files.readAllLines(dir.resolve("ddl.sql"));
            try (Statement st = c.createStatement()) {
                for (String d : ddl) if (!d.isBlank() && !d.startsWith("--")) st.execute(d);
            }
            for (String table : order) {
                List<String> lines = Files.readAllLines(dir.resolve(table + ".tsv"), StandardCharsets.UTF_8);
                if (lines.size() <= 1) continue;
                String[] cols = lines.get(0).split("\t", -1);
                List<String[]> types = columnTypes(c, table);
                StringBuilder nameList = new StringBuilder();
                StringBuilder valList = new StringBuilder();
                for (int i = 0; i < cols.length; i++) {
                    if (i > 0) { nameList.append(','); valList.append(','); }
                    nameList.append('"').append(cols[i]).append('"');
                    String type = types.get(i)[1].equals("text") ? "text" : types.get(i)[1];
                    valList.append("CAST(? AS ").append(type).append(')');
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO \"" + table + "\" (" + nameList + ") VALUES (" + valList + ")")) {
                    for (int i = 1; i < lines.size(); i++) {
                        String[] v = lines.get(i).split("\t", -1);
                        for (int j = 0; j < v.length; j++) {
                            ps.setString(j + 1, v[j].equals("\\N") ? null
                                    : v[j].replace("\\t", "\t").replace("\\n", "\n"));
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            c.commit();
            System.out.println("loaded " + db + " from " + dir);
        }
    }

    static List<String[]> columnTypes(Connection c, String table) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT column_name, data_type, udt_name, character_maximum_length,
                       numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position""")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = switch (rs.getString("data_type")) {
                        case "character varying" -> "varchar(" + rs.getInt("character_maximum_length") + ")";
                        case "character" -> "char(" + rs.getInt("character_maximum_length") + ")";
                        case "numeric" -> "numeric(" + rs.getInt("numeric_precision") + "," + rs.getInt("numeric_scale") + ")";
                        case "timestamp with time zone" -> "timestamptz";
                        case "timestamp without time zone" -> "timestamp";
                        case "ARRAY" -> rs.getString("udt_name").substring(1) + "[]";
                        default -> rs.getString("udt_name");
                    };
                    if (type.equals("varchar(0)")) type = "text";
                    out.add(new String[]{rs.getString("column_name"), type});
                }
            }
        }
        return out;
    }

    static void run(String db, Path file) throws Exception {
        String sql = Files.readString(file);
        try (Connection c = DriverManager.getConnection(BASE + db, USER, PASS)) {
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                boolean isResult = st.execute(sql);
                while (true) {
                    if (isResult) {
                        try (ResultSet rs = st.getResultSet()) {
                            print(rs);
                        }
                    }
                    if (!st.getMoreResults() && st.getUpdateCount() == -1) break;
                    isResult = st.getMoreResults();
                }
            }
        }
    }

    static void print(ResultSet rs) throws Exception {
        int n = rs.getMetaData().getColumnCount();
        StringBuilder h = new StringBuilder();
        for (int i = 1; i <= n; i++) { if (i > 1) h.append(" | "); h.append(rs.getMetaData().getColumnLabel(i)); }
        System.out.println(h);
        System.out.println("-".repeat(Math.min(h.length(), 110)));
        int count = 0;
        while (rs.next()) {
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= n; i++) { if (i > 1) line.append(" | "); line.append(rs.getString(i)); }
            System.out.println(line);
            count++;
        }
        System.out.println("(" + count + " rows)\n");
    }

    static void show(String db) throws Exception {
        try (Connection c = DriverManager.getConnection(BASE + db, USER, PASS);
             Statement st = c.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace " +
                    "WHERE n.nspname='public' AND relkind='r' ORDER BY 1")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            for (String t : tables) {
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM \"" + t + "\"")) {
                    rs.next();
                    long n = rs.getLong(1);
                    if (n > 0 || Set.of("users", "listings", "conversations", "messages", "collections",
                            "collection_items", "conversation_participants", "refresh_tokens", "books").contains(t)) {
                        System.out.printf("%-28s %6d%n", t, n);
                    }
                }
            }
        }
    }
}
