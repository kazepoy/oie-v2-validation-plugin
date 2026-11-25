/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openintegrationengine.plugins.hl7v2.core.ProductSet;
import org.openintegrationengine.plugins.hl7v2.core.Refusal;
import org.openintegrationengine.plugins.hl7v2.core.SetRegistry;
import org.openintegrationengine.plugins.hl7v2.core.Validator;

/**
 * Thread-safe facade over {@link SetRegistry} and {@link Validator}.
 * Used by {@link Hl7v2ValidationServlet} and directly callable from OIE channel scripts (Rhino JS).
 */
public final class Hl7v2ValidatorFacade {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Validator VALIDATOR = new Validator();
    private static final Object LOCK = new Object();

    private static volatile SetRegistry registry;
    private static volatile Path profilesDir;

    private Hl7v2ValidatorFacade() {
    }

    /**
     * Initializes or reconfigures the profile registry directory.
     */
    public static void configure(Path directory) {
        synchronized (LOCK) {
            Path resolved = directory.toAbsolutePath().normalize();
            if (Files.notExists(resolved)) {
                try {
                    Files.createDirectories(resolved);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create profiles directory: " + resolved, e);
                }
            }
            SetRegistry next = new SetRegistry(resolved);
            SetRegistry previous = registry;
            registry = next;
            profilesDir = resolved;
            if (previous != null) {
                previous.close();
            }
        }
    }

    /**
     * Reloads profile sets from the configured directory.
     */
    public static void reload() {
        SetRegistry current = requireRegistry();
        current.reload();
    }

    /**
     * Closes the active registry on plugin shutdown.
     */
    public static void close() {
        synchronized (LOCK) {
            if (registry != null) {
                registry.close();
                registry = null;
            }
        }
    }

    /**
     * Returns the configured profiles directory, or {@code null} if not configured.
     */
    public static Path getProfilesDir() {
        return profilesDir;
    }

    /**
     * Validates an HL7v2 ER7 message against a profile set using {@code hapi} or {@code nist}.
     */
    public static Validator.Result validate(String er7, String setName, String profile, String engine) {
        SetRegistry current = requireRegistry();
        return current.withSet(setName, set -> VALIDATOR.validate(er7, set, profile, engine));
    }

    /**
     * Validates an HL7v2 ER7 message and returns the validation {@link org.openintegrationengine.plugins.hl7v2.core.Report} as JSON.
     */
    public static String validateToReportJson(String er7, String setName, String profile, String engine) {
        return reportToJson(validate(er7, setName, profile, engine).report());
    }

    static String reportToJson(org.openintegrationengine.plugins.hl7v2.core.Report report) {
        return toJson(report);
    }

    /**
     * Validates an HL7v2 ER7 message and returns the human-readable text report.
     */
    public static String validateToText(String er7, String setName, String profile, String engine) {
        return validate(er7, setName, profile, engine).report().toText();
    }

    /**
     * Validates an HL7v2 ER7 message and serializes {@code {"report": ..., "ack": ...}} as JSON.
     */
    public static String validateToJson(String er7, String setName, String profile, String engine) {
        Validator.Result result = validate(er7, setName, profile, engine);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report", result.report());
        payload.put("ack", result.ack());
        return toJson(payload);
    }

    /**
     * Validates an HL7v2 ER7 message and returns the HL7 ACK string.
     */
    public static String validateToAck(String er7, String setName, String profile, String engine) {
        return validate(er7, setName, profile, engine).ack();
    }

    /**
     * Returns the current settings (configured profiles directory) as JSON.
     */
    public static String getSettingsJson() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("profilesDir", profilesDir != null ? profilesDir.toString() : "");
        return toJson(settings);
    }

    /**
     * Returns the loaded profile sets and their profiles as JSON.
     */
    public static String listSetsJson() {
        SetRegistry current = requireRegistry();
        List<Map<String, Object>> sets = current.describe(Hl7v2ValidatorFacade::describeSet);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profilesDir", profilesDir != null ? profilesDir.toString() : "");
        response.put("sets", sets);
        return toJson(response);
    }

    static String refusalToJson(Refusal refusal) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", refusal.code().name());
        err.put("status", refusal.status());
        err.put("message", refusal.getMessage());
        if (!refusal.candidates().isEmpty()) {
            err.put("candidates", refusal.candidates());
        }
        return toJson(err);
    }

    static Map<String, Object> parseJsonMap(String json) throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);
        return map;
    }

    private static Map<String, Object> describeSet(ProductSet set) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", set.name());
        item.put("version", set.version());
        item.put("hash", set.hash());
        List<Map<String, Object>> profiles = set.profiles().values().stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id());
            pm.put("messageType", p.key());
            pm.put("hl7Version", p.hl7Version());
            pm.put("hapi", p.hasHapi());
            pm.put("nist", p.hasNist());
            return pm;
        }).toList();
        item.put("profiles", profiles);
        return item;
    }

    private static SetRegistry requireRegistry() {
        SetRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("HL7v2 Validation plugin is not initialized.");
        }
        return current;
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON response", e);
        }
    }
}
