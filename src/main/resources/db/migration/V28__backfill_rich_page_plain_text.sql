-- Earlier rich-editor saves used the versioned JSON envelope before the
-- server-side extractor traversed its `document` payload. Repair only blank
-- projections so existing installations receive useful search results without
-- rewriting normal pages.
UPDATE pages
SET plain_text = CASE
    WHEN content IS JSON THEN COALESCE(
        (
            SELECT string_agg(DISTINCT value #>> '{}', ' ')
            FROM jsonb_path_query(content::jsonb, '$.document.**.text') AS value
        ),
        content
    )
    ELSE content
END
WHERE plain_text = ''
  AND content <> '';
