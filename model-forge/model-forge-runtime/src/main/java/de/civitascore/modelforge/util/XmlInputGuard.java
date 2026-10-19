package de.civitascore.modelforge.util;

import java.util.Locale;

/**
 * First-pass guard against unsafe XML input — specifically DOCTYPE and ENTITY declarations
 * that are the entry point for XXE (XML External Entity) attacks.
 *
 * <p>This is a fast case-insensitive string scan performed <em>before</em> any XML parser
 * is invoked. It is intentionally a heuristic, not a complete defence. The second
 * line of defence is {@link SecureXsdParser}, which configures the underlying
 * {@link javax.xml.parsers.DocumentBuilder} with {@code FEATURE_SECURE_PROCESSING}
 * and {@code disallow-doctype-decl}.
 */
public final class XmlInputGuard {

    private XmlInputGuard() {}

    public static void rejectUnsafeXml(String xml) {
        if (xml == null) return;
        String lower = xml.toLowerCase(Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<!entity")) {
            throw new IllegalArgumentException("Unsafe XML input: DOCTYPE and entity declarations are not allowed");
        }
    }
}
