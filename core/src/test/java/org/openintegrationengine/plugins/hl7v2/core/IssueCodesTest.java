package org.openintegrationengine.plugins.hl7v2.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.openintegrationengine.plugins.hl7v2.core.IssueCode.*;
import static org.junit.jupiter.api.Assertions.*;

class IssueCodesTest {

    // Gabarits de DefaultValidator, hapi-base 2.6.0, y compris les erreurs de profil.
    static Stream<Arguments> hapiTemplates() {
        return Stream.of(
                Arguments.of("Required element Family Name is missing", MISSING_REQUIRED),
                Arguments.of("PID not found in message", MISSING_REQUIRED),
                Arguments.of("Field 40 not found in message", MISSING_REQUIRED),
                Arguments.of("Element \"PID-4\" is present but specified as not used (X)", FORBIDDEN_PRESENT),
                Arguments.of("PID must have at least 1 repetitions (has 0)", CARDINALITY_MIN),
                Arguments.of("PID must have no more than 1 repetitions (has 2)", CARDINALITY_MAX),
                Arguments.of("The type ST has length 12 which exceeds max of 5", LENGTH),
                Arguments.of("Length of 12 exceeds maximum of 5", LENGTH),
                Arguments.of("'XX' doesn't equal constant value of 'YY'", CONSTANT),
                Arguments.of("HL7 datatype ST doesn't match profile datatype CX", DATATYPE),
                Arguments.of("A field has type primitive ST but the profile defines components", DATATYPE),
                Arguments.of("A component has primitive type ST but the profile defines subcomponents", DATATYPE),
                Arguments.of("Code 'XX' not found in table HL70001, profile ", CODE_NOT_IN_TABLE),
                Arguments.of("The structure ZZZ appears in the message but not in the profile", NOT_IN_PROFILE),
                Arguments.of("Field 4 in PID appears in the message but not in the profile", NOT_IN_PROFILE),
                Arguments.of("The following components are not defined in the profile: XX^", NOT_IN_PROFILE),
                Arguments.of("Event type A02 doesn't match profile type of A01", STRUCTURE),
                Arguments.of("Message type ORU doesn't match profile type of ADT", STRUCTURE),
                Arguments.of("Message structure ORU_R01 doesn't match profile type of ADT_A01", STRUCTURE),
                Arguments.of("Mismatch between a segment in the profile and the structure GROUP in the message", STRUCTURE),
                Arguments.of("Mismatch between a group in the profile and the structure PID in the message", STRUCTURE),
                Arguments.of("More components in profile than allowed in message: 5", STRUCTURE),
                Arguments.of("More subcomponents in profile than allowed in message: 3", STRUCTURE));
    }

    @ParameterizedTest
    @MethodSource("hapiTemplates")
    void classifiesHapiTemplates(String text, IssueCode expected) {
        assertEquals(expected, IssueCodes.fromHapi(text));
    }

    // Les gabarits viennent du jar charge, pas d'une copie qui pourrait rester verte apres sa mise a jour.
    static Stream<Arguments> nistTemplates() {
        Config report = ConfigFactory.load().getConfig("report");
        Map<String, IssueCode> expected = Map.ofEntries(
                Map.entry("Usage", MISSING_REQUIRED), Map.entry("Cardinality", CARDINALITY_MIN),
                Map.entry("Constant Value", CONSTANT), Map.entry("Length", LENGTH),
                Map.entry("Format", FORMAT), Map.entry("Extra", NOT_IN_PROFILE),
                Map.entry("Unescaped Separator", FORMAT), Map.entry("Unexpected", STRUCTURE),
                Map.entry("Invalid Content", STRUCTURE), Map.entry("Unresolved Field Model", STRUCTURE),
                Map.entry("CoConstraint Failure", ASSERTION), Map.entry("Constraint Failure", ASSERTION),
                Map.entry("Content Failure", ASSERTION), Map.entry("Predicate Failure", PREDICATE),
                Map.entry("EVS", CODE_NOT_IN_TABLE), Map.entry("Code Not Found", CODE_NOT_IN_TABLE),
                Map.entry("Coded Element", CODE_NOT_IN_TABLE), Map.entry("Single Code Failure", CODE_NOT_IN_TABLE),
                Map.entry("Co-Constraint", ASSERTION), Map.entry("External Value Set Spec Error", ENGINE_ERROR));
        return report.root().keySet().stream().sorted()
                .filter(key -> report.hasPath(key + ".template") && report.hasPath(key + ".classification"))
                .filter(key -> java.util.Set.of("Error", "Warning", "Specification Error")
                        .contains(report.getString(key + ".classification")))
                .map(key -> {
                    String classification = report.getString(key + ".classification");
                    String category = report.getString(key + ".category");
                    String text = report.getString(key + ".template").replace("%s", "1");
                    if (key.equals("cardinality")) {
                        text = "PID must be in the cardinality range of [2, 5]; 1 occurrences found";
                    }
                    IssueCode code = switch (key) {
                        case "x-usage", "w-usage" -> FORBIDDEN_PRESENT;
                        case "re-usage" -> MISSING_RECOMMENDED;
                        case "null-cardinality" -> CARDINALITY_MAX;
                        default -> "Specification Error".equals(classification) ? SPEC_ERROR : expected.get(category);
                    };
                    assertNotNull(code, "Nouveau gabarit NIST a classifier : " + key + " / " + category);
                    return Arguments.of(key, category, classification, text, code);
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nistTemplates")
    void classifiesNistTemplates(String key, String category, String classification, String text, IssueCode expected) {
        assertEquals(expected, IssueCodes.fromNist(category, classification, text));
    }

    @ParameterizedTest
    @CsvSource({
            "'PID must be in the cardinality range of [2, *]; 0 occurrences found', CARDINALITY_MIN",
            "'PID must be in the cardinality range of [0, 1]; 2 occurrences found', CARDINALITY_MAX"
    })
    void distinguishesNistCardinality(String text, IssueCode expected) {
        assertEquals(expected, IssueCodes.fromNist("Cardinality", "Error", text));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "PID-3(1)-5|PID.3.5", "PID(0)-3(2)-4-3|PID.3.4.3", "PID|PID",
            "/ADT_A01/PATIENT/PID-3(0)|PID.3", "ADT_A01/PATIENT|", "ADT_A01|", "patient name|"
    }, delimiter = '|')
    void elementIsAStructuralPath(String location, String expected) {
        assertEquals(expected == null ? "" : expected, IssueCodes.elementOf(location));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "PID[1]-3[2].4.3|PID-3(1)-4-3", "MSH[1]|MSH", "ADT_A01[1]|",
            "ADT_A01[1]/PATIENT[1]/PID[2]-3[1].5|PID-3(0)-5"
    }, delimiter = '|')
    void convertsNistPaths(String path, String expected) {
        assertEquals(expected == null ? "" : expected, IssueCodes.locationOfNistPath(path));
    }

    @Test
    void extractsTableAndValueWithoutUsingTheLabelApostrophe() {
        var issue = IssueCodes.hapiIssue("PID-3(0)-1", "Patient's code 'L'HERMITE' not found in table HL70001, profile ");
        assertEquals("0001", issue.table());
        assertEquals("L'HERMITE", issue.value());
        assertEquals("PID.3.1", issue.element());
        assertEquals("HAPI", issue.engine());
        assertEquals("ERROR", issue.severity());
        assertEquals("0203", IssueCodes.tableOf("Code 'XX' is not member of the value set [HL70203]"));
        assertEquals("LOCAL-UF", IssueCodes.tableOf("Value Set: [LOCAL-UF]"));
        assertEquals("", IssueCodes.tableOf("No table binding"));
        assertEquals("", IssueCodes.valueOf("Patient's name is missing"));
    }

    @Test
    void nistConstantReportsTheFoundValueRatherThanTheExpectedConstant() {
        var issue = IssueCodes.nistIssue("ERROR", "PID[1]-8[1]", "Constant Value", "Error",
                "The value of PID-8 must be 'F' but 'M' was found");
        assertEquals(CONSTANT, issue.code());
        assertEquals("M", issue.value());
    }

    @Test
    void unknownTemplatesStayVisible() {
        assertEquals(OTHER, IssueCodes.fromHapi("New HAPI detection"));
        assertEquals(OTHER, IssueCodes.fromNist("New category", "Error", "New detection"));
        assertEquals(OTHER, IssueCodes.fromHapi(null));
        assertEquals(OTHER, IssueCodes.fromNist(null, null, null));
        assertEquals("", IssueCodes.elementOf(null));
        assertEquals("", IssueCodes.locationOfNistPath(null));
    }
}
