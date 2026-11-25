package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.util.Terser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {
    @TempDir Path root;
    private final Validator validator = new Validator();

    @Test
    void resolvesAProfileAndProducesACodedReportAndAck() throws Exception {
        try (ProductSet set = ProductSet.open(Fixtures.PAM_ZIP)) {
            var result = validator.validate(Fixtures.example("adt-a01-admission-pam-fr.er7"), set, null, null);
            Report report = result.report();
            assertEquals(Fixtures.PAM + "/" + Fixtures.A01, report.profile());
            assertEquals(Report.HAPI, report.engine());
            assertEquals(set.hash(), report.set().hash());
            assertFalse(report.valid());
            assertTrue(report.issues().stream().noneMatch(i -> i.code() == IssueCode.OTHER));
            Terser ack = new Terser(HapiContexts.current().getPipeParser().parse(result.ack()));
            assertEquals("AE", ack.get("/MSA-1"));
            assertEquals(report.messageControlId(), ack.get("/MSA-2"));
            assertEquals(report.issues().size(), ack.getSegment("/ERR").getParent().getAll("ERR").length);
        }
    }

    @Test
    void acceptsAConformantAckAndKeepsItsControlId() throws Exception {
        String message = Fixtures.example("ack-pec-conforme.er7")
                .replace("|FRA|UNICODE UTF-8", "|FRA|UNICODE UTF-8|||2.11^IHE_FRANCE-2.11-PAM");
        try (ProductSet set = ProductSet.open(Fixtures.PAM_ZIP)) {
            for (String engine : List.of("hapi", "nist")) {
                var result = validator.validate(message, set, "PEC/ITI-31/ACK_ALL", engine);
                assertTrue(result.report().valid(), result.report().toText());
                assertEquals("AA", result.report().acknowledgmentCode());
                assertEquals("016", new Terser(HapiContexts.current().getPipeParser().parse(result.ack())).get("/MSA-2"));
            }
        }
    }

    @Test
    void aNistOnlySetIsResolvedAndCannotBeValidatedByHapi() throws Exception {
        Path dir = root.resolve("nist-only");
        try (ProductSet source = ProductSet.open(Fixtures.PAM_ZIP)) {
            for (String name : List.of("Profile.xml", "Constraints.xml", "ValueSets.xml")) {
                Fixtures.copyProduct(source, dir, "nist/" + name);
            }
        }
        try (ProductSet set = ProductSet.open(dir)) {
            String message = Fixtures.example("adt-a01-admission-pam-fr.er7");
            Report report = validator.validate(message, set, null, "nist").report();
            assertEquals("nist-only/" + Fixtures.A01, report.profile());
            assertTrue(report.issues().stream().allMatch(i -> i.engine().equals("NIST")));
            assertCode(ErrorCode.ENGINE_NOT_AVAILABLE, () -> validator.validate(message, set, null, "hapi"));
        }
    }

    @Test
    void brokenNistResourcesProduceARefusalRatherThanAValidWarning() throws Exception {
        Path dir = root.resolve("broken");
        try (ProductSet source = ProductSet.open(Fixtures.PAM_ZIP)) {
            Fixtures.copyProduct(source, dir, "nist/Profile.xml");
        }
        Files.writeString(dir.resolve("nist/Constraints.xml"), "<invalid>");
        try (ProductSet set = ProductSet.open(dir)) {
            assertCode(ErrorCode.VALIDATION_FAILED, () -> validator.validate(
                    Fixtures.example("adt-a01-admission-pam-fr.er7"), set, null, "nist"));
        }
    }

    @Test
    void refusesInvalidInputsAndUnavailableEngines() throws Exception {
        try (ProductSet set = ProductSet.open(Fixtures.PAM_ZIP)) {
            String message = Fixtures.example("adt-a01-admission-pam-fr.er7");
            assertCode(ErrorCode.EMPTY_BODY, () -> validator.validate(" ", set, null, null));
            assertCode(ErrorCode.SET_REQUIRED, () -> validator.validate(message, null, null, null));
            assertCode(ErrorCode.UNREADABLE_MESSAGE, () -> validator.validate("not HL7", set, null, null));
            assertCode(ErrorCode.INVALID_PARAMETER, () -> validator.validate(message, set, null, "both"));
            assertCode(ErrorCode.INVALID_PARAMETER, () -> validator.validate(message, set, Fixtures.PAM + "/" + Fixtures.A01, null));
            assertCode(ErrorCode.UNKNOWN_PROFILE, () -> validator.validate(message, set, "unknown", null));
            assertCode(ErrorCode.NO_PROFILE_FOR_MESSAGE, () -> validator.validate(
                    message.replace("ADT^A01^ADT_A01", "MDM^T02^MDM_T02"), set, null, null));
        }
        Fixtures.profile(root.resolve("hapi-only"), "A01", "ADT", "A01", "ADT_A01");
        try (ProductSet set = ProductSet.open(root.resolve("hapi-only"))) {
            assertCode(ErrorCode.ENGINE_NOT_AVAILABLE, () -> validator.validate(
                    Fixtures.example("adt-a01-admission-pam-fr.er7"), set, null, "nist"));
        }
    }

    private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(Refusal.class, action).code());
    }
}
