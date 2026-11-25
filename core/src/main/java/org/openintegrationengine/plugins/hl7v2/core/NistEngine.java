package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.model.Message;
import gov.nist.validation.report.Entry;
import hl7.v2.validation.SyncHL7Validator;
import hl7.v2.validation.ValidationContextBuilder;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contexte NIST par jeu ; aucun echec du moteur ne devient un verdict valide. */
public final class NistEngine implements Engine {
    @Override
    public String id() {
        return Report.NIST;
    }

    @Override
    public boolean covers(ProductSet.Profile profile) {
        return profile.hasNist();
    }

    @Override
    public List<Report.Issue> check(ProductSet set, ProductSet.Profile profile, Message message, String er7) {
        try {
            SyncHL7Validator validator = set.cache("nist/validator", () -> load(set));
            return issuesOf(validator.check(er7, profile.nistMessageId()));
        } catch (Exception | Error e) {
            // Le chargeur Scala utilise java.lang.Error pour un XML invalide. Les erreurs JVM restent fatales.
            if (e instanceof Error error && error.getClass() != Error.class && !(error instanceof AssertionError)) {
                throw error;
            }
            throw new Refusal(ErrorCode.VALIDATION_FAILED, "Validation NIST impossible : " + profile.id(), e);
        }
    }

    public void prepare(ProductSet set) {
        if (set.hasNist()) set.cache("nist/validator", () -> load(set));
    }

    private static SyncHL7Validator load(ProductSet set) {
        ValidationContextBuilder builder = new ValidationContextBuilder(
                new ByteArrayInputStream(set.bytes(ProductSet.NIST_PROFILE).orElseThrow()));
        set.bytes("nist/Constraints.xml").ifPresent(b -> builder.useConformanceContext(List.of(new ByteArrayInputStream(b))));
        set.bytes("nist/ValueSets.xml").ifPresent(b -> builder.useValueSetLibrary(new ByteArrayInputStream(b)));
        set.bytes("nist/ValueSetBindings.xml").ifPresent(b -> builder.useVsBindings(new ByteArrayInputStream(b)));
        set.bytes("nist/CoConstraints.xml").ifPresent(b -> builder.useCoConstraintsContext(new ByteArrayInputStream(b)));
        set.bytes("nist/Slicings.xml").ifPresent(b -> builder.useSlicingContext(new ByteArrayInputStream(b)));
        return new SyncHL7Validator(builder.getValidationContext());
    }

    static List<Report.Issue> issuesOf(gov.nist.validation.report.Report report) {
        // Meme detection emise par Predicate Failure et Usage : une seule anomalie, avec le code du predicat.
        Map<List<String>, Report.Issue> out = new LinkedHashMap<>();
        for (List<Entry> entries : report.getEntries().values()) {
            for (Entry entry : entries) {
                String severity = switch (entry.getClassification()) {
                    case "Error" -> Report.ERROR;
                    case "Warning", "Specification Error" -> Report.WARNING;
                    default -> null;
                };
                if (severity == null) {
                    continue;
                }
                Report.Issue issue = IssueCodes.nistIssue(severity, entry.getPath(), entry.getCategory(),
                        entry.getClassification(), entry.getDescription());
                List<String> key = List.of(issue.severity(), issue.location(), issue.message());
                out.merge(key, issue, (before, after) -> after.code() == IssueCode.PREDICATE ? after : before);
            }
        }
        return List.copyOf(out.values());
    }
}
