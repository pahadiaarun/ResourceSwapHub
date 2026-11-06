import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logical snapshot + restore verification for the production Aiven database.
 *
 * <p>dump   - reads every public table in foreign-key-safe order into one TSV file
 *            per table (ISO-8601 timestamps, \N for NULL) plus a manifest with row
 *            counts and SHA-256 digests. The connection is forced READ ONLY.
 * <p>verify - recreates the tables in a scratch schema on the LOCAL PostgreSQL
 *            instance (never production), reloads the TSVs and compares row counts
 *            per table with the manifest.
 *
 * Credentials come from resourceswaphub-backend/.env (production) and the local
 * development defaults; neither is printed.
 */
public class ProdSnapshot {

    static final String LOCAL_URL = "jdbc:postgresql://127.0.0.1:55432/bookbridge";
    static final String LOCAL_USER = "bookbridge";
    static final String LOCAL_PASS = "bookbridge_dev";
    static final String SCRATCH = "prod_restore_check";
    static final int QUERY_TIMEOUT = 60;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: dump|verify <dir>");
        Path dir = Path.of(args[1]);
        switch (args[0]) {
            case "dump" -> dump(dir);
            case "verify" -> verify(dir);
            case "ddl" -> writeDdl(dir);
            default -> throw new IllegalArgumentException("unknown command " + args[0]);
        }
    }

    static Connection prod() throws Exception {
        Map<String, String> env = ProdAudit.loadEnv(Path.of("resourceswaphub-backend/.env"));
        String url = env.get("BOOKBRIDGE_DB_URL");
        if (url == null || !url.contains("aivencloud.com")) {
            throw new IllegalStateException("Refusing: production URL missing or not Aiven");
        }
        System.out.println("target: aiven/" + url.replaceAll(".*/([^?]+).*", "$1") + " (read-only)");
        Connection c = DriverManager.getConnection(url, env.get("BOOKBRIDGE_DB_USER"), env.get("BOOKBRIDGE_DB_PASSWORD"));
        c.setAutoCommit(false);
        c.setReadOnly(true);
        try (Statement st = c.createStatement()) {
            st.execute("SET TRANSACTION READ ONLY");
        }
        return c;
    }

    static void dump(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Connection c = prod()) {
            List<String> order = tableOrder(c);
            StringBuilder manifest = new StringBuilder();
            manifest.append("# Logical snapshot of production bookbridge (Aiven)\n");
            manifest.append("# taken_at_utc=").append(Instant.now()).append('\n');
            manifest.append("# flyway=").append(maxFlyway(c)).append('\n');
            manifest.append("# server=").append(version(c)).append('\n');
            manifest.append("# format=table<TAB>rows<TAB>sha256\n");
            long total = 0;
            for (String table : order) {
                Snapshot s = writeTable(c, table, dir.resolve(table + ".tsv"));
                total += s.rows;
                manifest.append(table).append('\t').append(s.rows).append('\t').append(s.sha256).append('\n');
                System.out.printf("%-28s %6d rows  %s%n", table, s.rows, s.sha256.substring(0, 12));
                System.out.flush();
            }
            manifest.append("# total_rows=").append(total).append('\n');
            Files.writeString(dir.resolve("MANIFEST.tsv"), manifest.toString(), StandardCharsets.UTF_8);
            System.out.println("total rows: " + total);
            System.out.println("manifest: " + dir.resolve("MANIFEST.tsv").toAbsolutePath());
            c.rollback();
        }
    }

    record Snapshot(long rows, String sha256) {}

    /** Rebuildable public-schema DDL for the snapshot's tables (used by LocalDryRun). */
    static void writeDdl(Path dir) throws Exception {
        List<String> tables = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("MANIFEST.tsv"))) {
            if (line.startsWith("#") || line.isBlank()) continue;
            tables.add(line.split("\t")[0]);
        }
        StringBuilder sb = new StringBuilder("-- generated from production information_schema (types + FK constraints)\n");
        try (Connection c = prod()) {
            Map<String, List<String>> fks = new HashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT k.conrelid::regclass::text AS child, pg_get_constraintdef(k.oid) AS def
                     FROM pg_constraint k JOIN pg_namespace n ON n.oid = k.connamespace
                     WHERE k.contype IN ('f','p','u') AND n.nspname='public'""")) {
                while (rs.next()) {
                    fks.computeIfAbsent(rs.getString("child"), k -> new ArrayList<>()).add(rs.getString("def"));
                }
            }
            for (String table : tableOrder(c)) {
                if (!tables.contains(table)) continue;
                List<String[]> cols = columnTypes(c, table);
                sb.append("CREATE TABLE \"").append(table).append("\" (");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append('"').append(cols.get(i)[0]).append("\" ").append(cols.get(i)[1]);
                }
                for (String def : fks.getOrDefault(table, List.of())) {
                    sb.append(", ").append(def);
                }
                sb.append(");\n");
            }
            c.rollback();
        }
        Files.writeString(dir.resolve("ddl.sql"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + dir.resolve("ddl.sql"));
    }

    static Snapshot writeTable(Connection c, String table, Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long rows = 0;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\"")) {
            st.setQueryTimeout(QUERY_TIMEOUT);
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (i > 1) header.append('\t');
                header.append(meta.getColumnLabel(i));
            }
            header.append('\n');
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(header.toString());
                md.update(header.toString().getBytes(StandardCharsets.UTF_8));
                while (rs.next()) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) line.append('\t');
                        String v = rs.getString(i);
                        line.append(v == null ? "\\N" : v.replace("\t", "\\t").replace("\n", "\\n").replace("\r", ""));
                    }
                    line.append('\n');
                    w.write(line.toString());
                    md.update(line.toString().getBytes(StandardCharsets.UTF_8));
                    rows++;
                }
            }
        }
        return new Snapshot(rows, hex(md.digest()));
    }

    static void verify(Path dir) throws Exception {
        Map<String, Long> expected = new HashMap<>();
        for (String line : Files.readAllLines(dir.resolve("MANIFEST.tsv"))) {
            if (line.startsWith("#") || line.isBlank()) continue;
            String[] p = line.split("\t");
            expected.put(p[0], Long.parseLong(p[1]));
        }
        try (Connection prodConn = prod(); Connection loc = DriverManager.getConnection(LOCAL_URL, LOCAL_USER, LOCAL_PASS)) {
            System.out.println("restore target: local " + LOCAL_URL + " schema " + SCRATCH);
            loc.setAutoCommit(true);
            try (Statement st = loc.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + SCRATCH + " CASCADE");
                st.execute("CREATE SCHEMA " + SCRATCH);
            }
            List<String> order = tableOrder(prodConn);
            try (Statement st = loc.createStatement()) {
                for (String table : order) st.execute(ddl(prodConn, table));
            }
            Map<String, List<String[]>> types = new HashMap<>();
            for (String table : order) types.put(table, columnTypes(prodConn, table));
            for (String table : order) {
                List<String> lines = Files.readAllLines(dir.resolve(table + ".tsv"));
                if (lines.size() <= 1) continue;
                String[] cols = lines.get(0).split("\t", -1);
                List<String[]> meta = types.get(table);
                StringBuilder colList = new StringBuilder();
                StringBuilder valList = new StringBuilder();
                for (int i = 0; i < meta.size(); i++) {
                    if (i > 0) { colList.append(','); valList.append(','); }
                    colList.append('"').append(meta.get(i)[0]).append('"');
                    valList.append("CAST(? AS ").append(meta.get(i)[1]).append(')');
                }
                String sql = "INSERT INTO " + SCRATCH + ".\"" + table + "\" (" + colList +") VALUES (" + valList + ")";
                try (PreparedStatement ps = loc.prepareStatement(sql)) {
                    for (int i = 1; i < lines.size(); i++) {
                        String[] vals = lines.get(i).split("\t", -1);
                        for (int j = 0; j < vals.length; j++) {
                            ps.setString(j + 1, vals[j].equals("\\N") ? null
                                    : vals[j].replace("\\t", "\t").replace("\\n", "\n"));
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            int failures = 0;
            System.out.printf("%-28s %10s %10s  %s%n", "table", "expected", "restored", "match");
            for (String table : order) {
                long got;
                try (Statement st = loc.createStatement();
                     ResultSet rs = st.executeQuery("SELECT count(*) FROM " + SCRATCH + ".\"" + table + "\"")) {
                    rs.next();
                    got = rs.getLong(1);
                }
                long want = expected.getOrDefault(table, 0L);
                boolean ok = want == got;
                if (!ok) failures++;
                System.out.printf("%-28s %10d %10d  %s%n", table, want, got, ok ? "OK" : "MISMATCH");
            }
            try (Statement st = loc.createStatement()) {
                st.execute("DROP SCHEMA " + SCRATCH + " CASCADE");
            }
            System.out.println(failures == 0
                    ? "\nRESTORE VERIFIED: every table row count matches the snapshot."
                    : "\nRESTORE FAILED: " + failures + " mismatched table(s).");
            if (failures > 0) System.exit(1);
        }
    }

    static String ddl(Connection prodConn, String table) throws Exception {
        StringBuilder sb = new StringBuilder("CREATE TABLE " + SCRATCH + ".\"" + table + "\" (");
        List<String[]> cols = columnTypes(prodConn, table);
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(cols.get(i)[0]).append("\" ").append(cols.get(i)[1]);
        }
        return sb.append(")").toString();
    }

    /** (column_name, sql_type) pairs straight from production's information_schema. */
    static List<String[]> columnTypes(Connection prodConn, String table) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (Statement st = prodConn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT column_name, data_type, udt_name, character_maximum_length,
                        numeric_precision, numeric_scale
                 FROM information_schema.columns
                 WHERE table_schema='public' AND table_name='""" + table + "' ORDER BY ordinal_position")) {
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
                if (type.equals("varchar(0)") || type.equals("varchar(0)")) type = "text";
                if (type.equals("varchar(")) type = "text";
                out.add(new String[]{rs.getString("column_name"), type});
            }
        }
        return out;
    }

    /** Parents before children, derived from the live FK graph. */
    static List<String> tableOrder(Connection c) throws Exception {
        Map<String, Set<String>> parents = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT c.relname AS child, p.relname AS parent
                 FROM pg_constraint k
                 JOIN pg_class c ON c.oid = k.conrelid
                 JOIN pg_class p ON p.oid = k.confrelid
                 JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE k.contype='f' AND n.nspname='public' AND c.oid <> p.oid""")) {
            while (rs.next()) {
                parents.computeIfAbsent(rs.getString("child"), k -> new LinkedHashSet<>()).add(rs.getString("parent"));
            }
        }
        List<String> all = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                 WHERE n.nspname='public' AND c.relkind='r' ORDER BY 1""")) {
            while (rs.next()) all.add(rs.getString(1));
        }
        List<String> out = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        while (done.size() < all.size()) {
            boolean progressed = false;
            for (String t : all) {
                if (done.contains(t)) continue;
                Set<String> ps = parents.getOrDefault(t, Set.of());
                if (ps.stream().allMatch(p -> done.contains(p) || p.equals(t) || !all.contains(p))) {
                    out.add(t);
                    done.add(t);
                    progressed = true;
                }
            }
            if (!progressed) {
                for (String t : all) if (!done.contains(t)) { out.add(t); done.add(t); }
            }
        }
        return out;
    }

    static String version(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version()")) {
            return rs.next() ? rs.getString(1).split(" on ")[0] : "?";
        }
    }

    static String maxFlyway(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT max(string_to_array(version,'.')::int[])::text FROM flyway_schema_history WHERE success")) {
            return rs.next() ? rs.getString(1) : "?";
        }
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
