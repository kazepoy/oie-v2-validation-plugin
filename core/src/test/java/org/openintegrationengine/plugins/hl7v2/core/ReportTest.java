package org.openintegrationengine.plugins.hl7v2.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReportTest {

    private static final Report.SetRef SET = new Report.SetRef("test-set", "1", "sha256:" + "0".repeat(64));

    @Test
    void jsonKeepsV1FieldsAndOnlyAddsTheDocumentedV2Fields() throws Exception {
        Report report = Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01", "2.5", "TEST-1",
                Report.HAPI, SET, List.of(IssueCodes.hapiIssue("PID-3(1)-5",
                        "Code 'XX' not found in table HL70203, profile ")));
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsBytes(report));
        try (var fixture = getClass().getResourceAsStream("/report-v2.json")) {
            assertNotNull(fixture);
            assertEquals(new ObjectMapper().readTree(fixture), json);
        }
        assertEquals(Set.of("schema", "valid", "acknowledgmentCode", "profile", "messageType", "hl7Version",
                "messageControlId", "engine", "set", "counts", "issues"), keys(json));
        var issue = json.get("issues").get(0);
        assertEquals(Set.of("severity", "location", "message", "engine", "code", "element", "table", "value"), keys(issue));
        assertEquals("CODE_NOT_IN_TABLE", issue.get("code").asText());
        assertEquals("ERROR", issue.get("severity").asText());
        assertEquals("PID.3.5", issue.get("element").asText());
        assertEquals(1, json.get("counts").get("error").asInt());
        assertEquals("AE", json.get("acknowledgmentCode").asText());
        assertEquals(report, new ObjectMapper().treeToValue(json, Report.class));
    }

    @Test
    void verdictComesFromIssuesAndWarningsDoNotInvalidate() {
        var warning = IssueCodes.nistIssue("WARNING", "PID[1]-7[1]", "Usage", "Warning",
                "PID-7 is missing. Depending on the use case it may be appropriate to value this element");
        Report report = Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01", "2.5", "TEST-1",
                Report.NIST, SET, List.of(warning));
        assertTrue(report.valid());
        assertEquals("AA", report.acknowledgmentCode());
        assertEquals(0, report.errorCount());
        assertEquals(1, report.warningCount());
    }

    @Test
    void countsErrorsAndWarningsSeparately() {
        var error = IssueCodes.nistIssue("ERROR", "PID[1]-3[1]", "Usage", "Error", "The required PID-3 is missing");
        var warning = IssueCodes.nistIssue("WARNING", "PID[1]-7[1]", "Usage", "Warning",
                "PID-7 is missing. Depending on the use case it may be appropriate to value this element");
        var report = Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01", "2.5", "TEST-1", Report.NIST, SET,
                List.of(error, warning, error));
        assertFalse(report.valid());
        assertEquals("AE", report.acknowledgmentCode());
        assertEquals(new Report.Counts(2, 1), report.counts());
    }

    @Test
    void issuesAreAnImmutableSnapshot() {
        var issues = new ArrayList<Report.Issue>();
        issues.add(IssueCodes.hapiIssue("PID-3", "Required element Identifier is missing"));
        var report = Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01", "2.5", "TEST-1", Report.HAPI, SET, issues);
        issues.clear();
        assertEquals(1, report.issues().size());
        assertThrows(UnsupportedOperationException.class, () -> report.issues().clear());
    }

    @Test
    void aReportCannotMergeEnginesOrTreatAnUnknownSeverityAsAWarning() {
        var hapi = IssueCodes.hapiIssue("PID-3", "Required element Identifier is missing");
        assertThrows(IllegalArgumentException.class, () -> Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01",
                "2.5", "TEST-1", Report.NIST, SET, List.of(hapi)));
        assertThrows(IllegalArgumentException.class, () -> Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01",
                "2.5", "TEST-1", "both", SET, List.of()));
        var alert = new Report.Issue("Alert", "", "", Report.NIST, IssueCode.OTHER, "", "", "");
        assertThrows(IllegalArgumentException.class, () -> Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01",
                "2.5", "TEST-1", Report.NIST, SET, List.of(alert)));
    }

    @Test
    void noIssuesMeansAValidReport() {
        var report = Report.of("test-set/ADT_A01", "ADT^A01^ADT_A01", "2.5", "TEST-1", Report.HAPI, SET, List.of());
        assertTrue(report.valid());
        assertEquals("AA", report.acknowledgmentCode());
        assertEquals(new Report.Counts(0, 0), report.counts());
    }

    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode json) {
        var keys = new java.util.HashSet<String>();
        json.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
