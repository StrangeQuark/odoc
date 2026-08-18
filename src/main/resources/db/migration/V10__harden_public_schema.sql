-- The current Phase-0 schema deliberately remains in PostgreSQL's public schema.
-- Disable untrusted CREATE there before any runtime role is granted access: application
-- sessions use an explicit public-only search path and cannot be shadowed by arbitrary
-- objects from the PUBLIC pseudo-role.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
