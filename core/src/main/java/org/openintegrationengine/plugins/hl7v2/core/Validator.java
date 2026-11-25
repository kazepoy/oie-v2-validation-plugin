package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validation sans Spring. Partageable entre fils ; le jeu reste ouvert pendant l'appel. */
public final class Validator {
    private final Map<String, Engine> engines = Map.of(Report.HAPI, new HapiEngine(), Report.NIST, new NistEngine());

    public record Result(Report report, String ack) {
    }

    public Result validate(String er7, ProductSet set, String profile, String requestedEngine) {
        if (er7 == null || er7.isBlank()) {
            throw new Refusal(ErrorCode.EMPTY_BODY, "Le corps de la requete est vide");
        }
        if (set == null) {
            throw new Refusal(ErrorCode.SET_REQUIRED, "Le jeu de profils est obligatoire");
        }
        String engineId = requestedEngine == null || requestedEngine.isBlank()
                ? Report.HAPI : requestedEngine.trim().toUpperCase(Locale.ROOT);
        Engine engine = engines.get(engineId);
        if (engine == null) {
            throw new Refusal(ErrorCode.INVALID_PARAMETER, "Moteur inconnu : " + requestedEngine);
        }
        String cr = Hl7.eolToCr(er7);
        Message message;
        try {
            message = HapiContexts.current().getPipeParser().parse(cr);
        } catch (Exception e) {
            throw new Refusal(ErrorCode.UNREADABLE_MESSAGE, "Message HL7v2 illisible", e);
        }
        String type;
        String event;
        String structure;
        String controlId;
        String version;
        try {
            Terser terser = new Terser(message);
            type = nz(terser.get("/.MSH-9-1"));
            event = nz(terser.get("/.MSH-9-2"));
            structure = nz(terser.get("/.MSH-9-3"));
            controlId = nz(terser.get("/.MSH-10"));
            version = nz(terser.get("/.MSH-12-1"));
        } catch (Exception e) {
            throw new Refusal(ErrorCode.UNREADABLE_MSH, "Segment MSH illisible", e);
        }
        ProductSet.Profile resolved;
        if (profile == null || profile.isBlank()) {
            if (type.isEmpty() && event.isEmpty() && structure.isEmpty()) {
                throw new Refusal(ErrorCode.UNREADABLE_MSH, "MSH-9 inexploitable");
            }
            resolved = Resolver.resolve(set, type, event, structure);
        } else {
            String relative = profile.trim().replace('\\', '/');
            if (relative.startsWith(set.name() + "/") || relative.startsWith("/")
                    || List.of(relative.split("/", -1)).contains("..")) {
                throw new Refusal(ErrorCode.INVALID_PARAMETER, "Le parametre profile est relatif au jeu");
            }
            resolved = set.profile(relative).orElseThrow(() -> new Refusal(ErrorCode.UNKNOWN_PROFILE,
                    "Profil " + relative + " absent du jeu " + set.name()));
        }
        if (!engine.covers(resolved)) {
            throw new Refusal(ErrorCode.ENGINE_NOT_AVAILABLE, "Le moteur " + engineId + " ne couvre pas " + resolved.id());
        }
        List<Report.Issue> issues = engine.check(set, resolved, message, cr);
        Report report = Report.of(set.name() + "/" + resolved.id(), Resolver.label(type, event, structure), version,
                controlId, engineId, new Report.SetRef(set.name(), set.version(), set.hash()), issues);
        return new Result(report, Acks.acknowledge(message, report));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
