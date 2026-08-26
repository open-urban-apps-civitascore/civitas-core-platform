-- Consolidate the UML type vocabulary of the data structure modeller.
--
-- `styles` is not only layout: it carries the whole UML diagram, and the modeller reconstructs
-- itself from there (there is no JSON-Schema-to-UML importer). It is also the mapping editor's
-- fallback tree when `model` is absent or unresolvable. The type names therefore have to be
-- rewritten here, while `model` — holding the exported JSON Schema — needs no change: every
-- rewrite below maps onto the fragment the old name already exported.
--
-- `Date` is the one rename that is not merely cosmetic. It keeps its name but changes meaning:
-- it exported `format: date-time` (a TIMESTAMPTZ column) and will denote a date without time
-- (`format: date`, a DATE column). Left untouched, every stored timestamp would silently lose
-- its time on the next deployment, with the model staying formally valid throughout. Renaming to
-- `DateTime` preserves the existing semantics and frees `Date` for the narrower one.
--
-- `void` becomes `String` rather than dropping the attribute: it exported `type: "null"`, which
-- the PostGIS mapper resolves through its default branch to TEXT — the same column `String`
-- produces. Removing it would change the target table instead of preserving it.

-- `styles` is an unvalidated jsonb column, so an absent member list may arrive either as a missing
-- key or as an explicit JSON null. COALESCE only covers the first; JSONB_ARRAY_ELEMENTS rejects the
-- second. Every array access below goes through here so both shapes collapse to the same empty list.
CREATE OR REPLACE FUNCTION pg_temp.json_array_or_empty(value JSONB) RETURNS JSONB AS
$$
SELECT CASE WHEN JSONB_TYPEOF(value) = 'array' THEN value ELSE '[]'::JSONB END;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.renamed_uml_type(type_value JSONB) RETURNS JSONB AS
$$
-- A UML type is either a primitive name or a UMLTypeReference object. Only the string form names
-- a primitive; a class merely called "Date" must survive untouched, so the object form is
-- returned unchanged.
SELECT CASE
           WHEN JSONB_TYPEOF(type_value) <> 'string' THEN type_value
           ELSE COALESCE('{
                  "Date": "DateTime",
                  "Float": "Number",
                  "Double": "Number",
                  "Long": "Integer",
                  "Short": "Integer",
                  "Byte": "Integer",
                  "Character": "String",
                  "void": "String"
                }'::JSONB -> (type_value #>> '{}'), type_value)
           END;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.rewrite_typed_members(members JSONB) RETURNS JSONB AS
$$
SELECT COALESCE(
               (SELECT JSONB_AGG(
                               CASE
                                   WHEN member ? 'type'
                                       THEN JSONB_SET(member, '{type}', pg_temp.renamed_uml_type(member -> 'type'))
                                   ELSE member
                                   END
                               ORDER BY ordinality)
                FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(members))
                         WITH ORDINALITY AS t(member, ordinality)),
               members);
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.rewrite_operations(operations JSONB) RETURNS JSONB AS
$$
SELECT COALESCE(
               (SELECT JSONB_AGG(
                               CASE
                                   WHEN JSONB_TYPEOF(operation -> 'parameters') = 'array'
                                       THEN JSONB_SET(operation, '{parameters}',
                                                      pg_temp.rewrite_typed_members(operation -> 'parameters'))
                                   ELSE operation
                                   END
                                   ||
                               CASE
                                   WHEN operation ? 'returnType'
                                       THEN JSONB_BUILD_OBJECT('returnType',
                                                               pg_temp.renamed_uml_type(operation -> 'returnType'))
                                   ELSE '{}'::JSONB
                                   END
                               ORDER BY ordinality)
                FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(operations))
                         WITH ORDINALITY AS t(operation, ordinality)),
               operations);
$$ LANGUAGE SQL IMMUTABLE;

-- `element.type` is the element kind ('class', 'enumeration', …) and shares the key name with an
-- attribute's UML type, so only these three locations may be touched: attributes[], and an
-- operation's returnType and parameters[].
CREATE OR REPLACE FUNCTION pg_temp.rewrite_element(element JSONB) RETURNS JSONB AS
$$
SELECT CASE
           WHEN JSONB_TYPEOF(element) <> 'object' THEN element
           ELSE element
               ||
                CASE
                    WHEN JSONB_TYPEOF(element -> 'attributes') = 'array'
                        THEN JSONB_BUILD_OBJECT('attributes',
                                                pg_temp.rewrite_typed_members(element -> 'attributes'))
                    ELSE '{}'::JSONB
                    END
               ||
                CASE
                    WHEN JSONB_TYPEOF(element -> 'operations') = 'array'
                        THEN JSONB_BUILD_OBJECT('operations', pg_temp.rewrite_operations(element -> 'operations'))
                    ELSE '{}'::JSONB
                    END
           END;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.rewrite_nodes(nodes JSONB) RETURNS JSONB AS
$$
SELECT COALESCE(
               (SELECT JSONB_AGG(
                               CASE
                                   WHEN JSONB_TYPEOF(node -> 'data' -> 'element') = 'object'
                                       THEN JSONB_SET(node, '{data,element}',
                                                      pg_temp.rewrite_element(node -> 'data' -> 'element'))
                                   ELSE node
                                   END
                               ORDER BY ordinality)
                FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(nodes))
                         WITH ORDINALITY AS t(node, ordinality)),
               nodes);
$$ LANGUAGE SQL IMMUTABLE;

-- Reaches the same three type locations as the rewrite. That keeps the two in step, but it also
-- bounds the verification: a type stored anywhere else is missed by both and still reports success.
-- Only nodes carry attributes and operations, so no such location exists today.
CREATE OR REPLACE FUNCTION pg_temp.uml_type_values(styles JSONB) RETURNS SETOF JSONB AS
$$
SELECT member -> 'type'
FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(styles -> 'nodes')) AS node,
     JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(node -> 'data' -> 'element' -> 'attributes')) AS member
UNION ALL
SELECT operation -> 'returnType'
FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(styles -> 'nodes')) AS node,
     JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(node -> 'data' -> 'element' -> 'operations')) AS operation
UNION ALL
SELECT parameter -> 'type'
FROM JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(styles -> 'nodes')) AS node,
     JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(node -> 'data' -> 'element' -> 'operations')) AS operation,
     JSONB_ARRAY_ELEMENTS(pg_temp.json_array_or_empty(operation -> 'parameters')) AS parameter;
$$ LANGUAGE SQL STABLE;

-- Where the diagram lives differs by branch, so the rewrite is applied per storage shape:
--
--   * `data_structure_versions.styles` — the pre-Model-Forge shape this migration was written
--     against. Still present upstream; dropped here by V1_2_10 (drop_inline_model_payloads).
--   * `model_forge.artifact.content -> 'x-ui-styles'` — where the Model Forge integration keeps
--     the same diagram. The gateway merges the host's `styles` map into the stored document under
--     that keyword and splits it off again on read, so the node shape is identical and the
--     rewrite functions above apply unchanged.
--
-- Both branches are guarded on existence rather than assumed, so this file runs on either shape
-- without a second migration having to know which one it met.
DO
$$
    DECLARE
        migrated_rows  BIGINT;
        stale_versions BIGINT;
    BEGIN
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'data_structure_versions'
                     AND column_name = 'styles') THEN
            -- An IMMUTABLE function is not eliminated as a common subexpression across SET and
            -- WHERE, so the rewrite is computed once in a subquery rather than twice per row.
            UPDATE data_structure_versions v
            SET styles = candidate.rewritten
            FROM (SELECT id,
                         JSONB_SET(styles, '{nodes}', pg_temp.rewrite_nodes(styles -> 'nodes')) AS rewritten
                  FROM data_structure_versions
                  WHERE styles IS NOT NULL
                    AND JSONB_TYPEOF(styles -> 'nodes') = 'array') AS candidate
            WHERE v.id = candidate.id
              AND candidate.rewritten IS DISTINCT FROM v.styles;

            GET DIAGNOSTICS migrated_rows = ROW_COUNT;
            RAISE INFO 'UML type consolidation rewrote % data structure version(s)', migrated_rows;

            -- A retired name left anywhere means the rewrite missed a location the diagram
            -- actually uses. Failing here is the point: those names resolve to a different column
            -- type downstream, so a partial migration is worse than none.
            SELECT count(*)
            INTO stale_versions
            FROM data_structure_versions v
            WHERE v.styles IS NOT NULL
              AND EXISTS (SELECT 1
                          FROM pg_temp.uml_type_values(v.styles) AS type_value
                          WHERE pg_temp.renamed_uml_type(type_value) IS DISTINCT FROM type_value);

            IF stale_versions > 0 THEN
                RAISE EXCEPTION 'UML type consolidation incomplete; % version(s) still carry a retired type name', stale_versions;
            END IF;
        END IF;

        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'model_forge'
                     AND table_name = 'artifact_representation'
                     AND column_name = 'content_jsonb') THEN
            UPDATE model_forge.artifact_representation r
            SET content_jsonb = candidate.rewritten
            FROM (SELECT id,
                         JSONB_SET(content_jsonb,
                                   '{x-ui-styles,nodes}',
                                   pg_temp.rewrite_nodes(content_jsonb -> 'x-ui-styles' -> 'nodes')) AS rewritten
                  FROM model_forge.artifact_representation
                  WHERE JSONB_TYPEOF(content_jsonb -> 'x-ui-styles' -> 'nodes') = 'array') AS candidate
            WHERE r.id = candidate.id
              AND candidate.rewritten IS DISTINCT FROM r.content_jsonb;

            GET DIAGNOSTICS migrated_rows = ROW_COUNT;
            RAISE INFO 'UML type consolidation rewrote % Model Forge representation(s)', migrated_rows;

            SELECT count(*)
            INTO stale_versions
            FROM model_forge.artifact_representation r
            WHERE EXISTS (SELECT 1
                          FROM pg_temp.uml_type_values(r.content_jsonb -> 'x-ui-styles') AS type_value
                          WHERE pg_temp.renamed_uml_type(type_value) IS DISTINCT FROM type_value);

            IF stale_versions > 0 THEN
                RAISE EXCEPTION 'UML type consolidation incomplete; % representation(s) still carry a retired type name', stale_versions;
            END IF;
        END IF;
    END
$$;

-- Flyway runs every pending migration on one connection, and pg_temp outlives this file for the
-- whole session — where it also resolves ahead of `public`. Dropping the helpers keeps a later
-- migration free to use these names.
DROP FUNCTION pg_temp.uml_type_values(JSONB);
DROP FUNCTION pg_temp.rewrite_nodes(JSONB);
DROP FUNCTION pg_temp.rewrite_element(JSONB);
DROP FUNCTION pg_temp.rewrite_operations(JSONB);
DROP FUNCTION pg_temp.rewrite_typed_members(JSONB);
DROP FUNCTION pg_temp.renamed_uml_type(JSONB);
DROP FUNCTION pg_temp.json_array_or_empty(JSONB);
