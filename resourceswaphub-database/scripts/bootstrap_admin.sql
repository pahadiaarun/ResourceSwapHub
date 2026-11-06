-- Promote one existing account to ADMIN so the Phase 1 moderation endpoints
-- (GET/PATCH /api/v1/admin/reports, POST /api/v1/admin/listings/{id}/moderation)
-- become reachable. ModerationService.requireAdmin is the only role check, so
-- without an ADMIN row the report -> review -> takedown flow cannot be used at
-- all, and a reported digital resource stays live.
--
-- Deliberately NOT an endpoint or a migration. An API that can grant ADMIN is a
-- self-escalation surface, and a migration cannot know which account belongs to
-- the operator. This is a one-time, human-run, auditable action.
--
-- Usage (the local stack, or any environment whose credentials are in the env):
--   edit target_email below, then
--   java -cp <postgresql.jar> resourceswaphub-database/scripts/DatabaseTool.java \
--        run resourceswaphub-database/scripts/bootstrap_admin.sql
--
-- Safe to re-run: the role is idempotent, and the trigger maintains updated_at.
-- It aborts instead of doing nothing if the address matches zero or several
-- accounts, because a silently unmatched UPDATE would leave the operator
-- believing an admin exists when none does.

DO $$
DECLARE
    -- Edit this one line. Nothing here guesses who the administrator should be.
    target_email CONSTANT text := 'REPLACE-WITH-ADMIN-EMAIL';
    affected         integer;
    admin_count      integer;
BEGIN
    IF target_email LIKE 'REPLACE%' THEN
        RAISE EXCEPTION 'Set target_email to a real registered account first.';
    END IF;

    UPDATE users
       SET role = 'ADMIN'
     WHERE lower(email) = lower(target_email);

    GET DIAGNOSTICS affected = ROW_COUNT;

    IF affected <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one account for %, matched %. Nothing was changed.',
            target_email, affected;
    END IF;

    SELECT count(*) INTO admin_count FROM users WHERE role = 'ADMIN';
    RAISE NOTICE '% admin account(s) now exist.', admin_count;
END
$$;

-- Confirmation: who can moderate, and whether the account they act on exists.
SELECT email, display_name, role
  FROM users
 WHERE role = 'ADMIN'
 ORDER BY email;
