import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the APPROVED production cleanup script as one transaction.
 *
 * <p>Safety model:
 * <ol>
 *   <li>refuses to run without the literal confirmation token; </li>
 *   <li>refuses unless the target is the Aiven production host and database `bookbridge`;</li>
 *   <li>captures per-table counts before and after the deletes *inside the same transaction*;</li>
 *   <li>commits only if every changed table matches the approved delta and every other
 *       table is untouched — otherwise it rolls back and exits non-zero.</li>
 * </ol>
 */
public class ProdApply {

    static final String TOKEN = "APPROVED-60-ROWS";

    /** Approved deletion delta, per table. Everything else must show 0 change. */
    static final Map<String, Long> EXPECTED = Map.of(
            "messages", 8L,
            "conversation_participants", 8L,
            "conversations", 4L,
            "collection_items", 4L,
            "collections", 2L,
            "listings", 11L,
            "refresh_tokens", 15L,
            "users", 7L,
            "books", 1L);

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[1].equals(TOKEN)) {
            throw new IllegalStateException("Refusing to run: pass the confirmation token " + TOKEN);
        }
        Path sqlFile = Path.of(args[0]);

        Map<String, String> env = ProdAudit.loadEnv(Path.of("resourceswaphub-backend/.env"));
        String url = env.get("BOOKBRIDGE_DB_URL");
        if (url == null || !url.contains("aivencloud.com")) {
            throw new IllegalStateException("Refusing: not the Aiven production host");
        }

        StringBuilder log = new StringBuilder();
        log.append("# Production cleanup execution log\n");
        log.append("started_utc=").append(Instant.now()).append('\n');
        log.append("script=").append(sqlFile).append('\n');

        try (Connection c = DriverManager.getConnection(
                url, env.get("BOOKBRIDGE_DB_USER"), env.get("BOOKBRIDGE_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            String db = one(c, "SELECT current_database()");
            String addr = one(c, "SELECT coalesce(inet_server_addr()::text,'?')");
            String flyway = one(c, "SELECT max(string_to_array(version,'.')::int[])::text FROM flyway_schema_history WHERE success");
            System.out.println("target: " + db + " @ " + addr + "  flyway=" + flyway);
            log.append("target=").append(db).append('@').append(addr).append(" flyway=").append(flyway).append('\n');
            if (!"bookbridge".equals(db)) throw new IllegalStateException("Refusing: database is " + db);

            Map<String, Long> before = counts(c);
            System.out.println("captured pre-cleanup counts for " + before.size() + " tables");

            String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
            System.out.println("executing " + sqlFile + " (" + sql.lines().count() + " lines, single transaction)");
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
            System.out.println("script executed; verifying deltas before COMMIT");

            Map<String, Long> after = counts(c);

            boolean ok = true;
            List<String> changed = new ArrayList<>();
            log.append("\n| table | before | after | delta | expected | verdict |\n|---|---|---|---|---|---|\n");
            for (Map.Entry<String, Long> e : before.entrySet()) {
                long b = e.getValue();
                long a = after.getOrDefault(e.getKey(), 0L);
                long delta = a - b;
                long expected = -EXPECTED.getOrDefault(e.getKey(), 0L);
                boolean good = delta == expected;
                if (!good) ok = false;
                if (delta != 0) {
                    changed.add(e.getKey());
                    System.out.printf("  %-28s %6d -> %6d  delta %+d (expected %+d) %s%n",
                            e.getKey(), b, a, delta, expected, good ? "OK" : "MISMATCH");
                }
                log.append("| ").append(e.getKey()).append(" | ").append(b).append(" | ").append(a)
                   .append(" | ").append(delta).append(" | ").append(expected).append(" | ")
                   .append(good ? "OK" : "MISMATCH").append(" |\n");
            }

            long totalDeleted = changed.stream().mapToLong(t -> before.get(t) - after.get(t)).sum();
            System.out.println("changed tables: " + String.join(", ", changed));
            System.out.println("total rows deleted: " + totalDeleted + " (expected 60)");

            if (!ok || totalDeleted != 60) {
                c.rollback();
                log.append("\nRESULT=ROLLED BACK (delta mismatch)\n");
                Files.writeString(Path.of("docs/production_cleanup_execution_log.md"), log.toString(),
                        StandardCharsets.UTF_8);
                System.err.println("ABORTED: deltas did not match the approved plan. Transaction rolled back.");
                System.exit(1);
            }

            c.commit();
            System.out.println("COMMITTED");
            log.append("\ntotal_deleted=").append(totalDeleted).append('\n');
            log.append("RESULT=COMMITTED\n");
            log.append("committed_utc=").append(Instant.now()).append('\n');

            StringBuilder post = new StringBuilder("\npost-cleanup counts:\n");
            after.forEach((t, n) -> {
                if (n > 0) post.append("  ").append(String.format("%-28s %6d%n", t, n));
            });
            System.out.print(post);
            log.append(post);

            Files.writeString(Path.of("docs/production_cleanup_execution_log.md"), log.toString(),
                    StandardCharsets.UTF_8);
            System.out.println("log: docs/production_cleanup_execution_log.md");
        }
    }

    /** Row counts for every table in schema public. */
    static Map<String, Long> counts(Connection c) throws Exception {
        Map<String, Long> out = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                 WHERE n.nspname='public' AND c.relkind='r' ORDER BY 1""")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        for (String t : tables) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM \"" + t + "\"")) {
                rs.next();
                out.put(t, rs.getLong(1));
            }
        }
        return out;
    }

    static String one(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "?";
        }
    }
}
