/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Content-type negotiation for HL7v2 validation responses (ER7 ACK, plain text, or JSON).
 * Defaults to ER7 ACK when {@code Accept} is absent, empty, or {@code *&#47;*}.
 */
public final class MediaTypes {

    public static final String HL7V2_ER7 = "application/hl7-v2+er7";
    public static final String HL7V2_ER7_UTF8 = HL7V2_ER7 + ";charset=UTF-8";
    public static final String TEXT_PLAIN_UTF8 = "text/plain;charset=UTF-8";
    public static final String APPLICATION_JSON = "application/json";

    public enum ResponseFormat { ER7, TEXT, JSON }

    private MediaTypes() {
    }

    /**
     * Resolves the response format from the HTTP {@code Accept} header by descending quality factor.
     */
    public static ResponseFormat responseFormat(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return ResponseFormat.ER7;
        }
        List<AcceptEntry> entries = parseAccept(acceptHeader);
        entries.sort(Comparator.comparingDouble(AcceptEntry::q).reversed());
        for (AcceptEntry entry : entries) {
            if (isEr7Subtype(entry.subtype())) {
                return ResponseFormat.ER7;
            }
            if (isJsonSubtype(entry.subtype())) {
                return ResponseFormat.JSON;
            }
            if (isTextSubtype(entry.subtype())) {
                return ResponseFormat.TEXT;
            }
        }
        return ResponseFormat.ER7;
    }

    public static String contentTypeOf(ResponseFormat format) {
        return switch (format) {
            case ER7 -> HL7V2_ER7_UTF8;
            case TEXT -> TEXT_PLAIN_UTF8;
            case JSON -> APPLICATION_JSON;
        };
    }

    private static List<AcceptEntry> parseAccept(String header) {
        List<AcceptEntry> result = new ArrayList<>();
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] segments = trimmed.split(";");
            String mediaRange = segments[0].trim();
            int slash = mediaRange.indexOf('/');
            if (slash < 0) {
                continue;
            }
            String subtype = mediaRange.substring(slash + 1).trim().toLowerCase(Locale.ROOT);
            double q = 1.0;
            for (int i = 1; i < segments.length; i++) {
                String param = segments[i].trim();
                if (param.toLowerCase(Locale.ROOT).startsWith("q=")) {
                    try {
                        q = Double.parseDouble(param.substring(2).trim());
                    } catch (NumberFormatException ignored) {
                        q = 1.0;
                    }
                }
            }
            result.add(new AcceptEntry(subtype, q));
        }
        return result;
    }

    private static boolean isEr7Subtype(String sub) {
        return "hl7-v2+er7".equals(sub) || sub.endsWith("+er7");
    }

    private static boolean isJsonSubtype(String sub) {
        return "json".equals(sub) || sub.endsWith("+json");
    }

    private static boolean isTextSubtype(String sub) {
        return "plain".equals(sub) || "txt".equals(sub) || sub.endsWith("+txt");
    }

    private record AcceptEntry(String subtype, double q) {
    }
}
