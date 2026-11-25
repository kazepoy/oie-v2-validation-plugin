package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.util.Terser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcksTest {
    @ParameterizedTest
    @ValueSource(strings = {"2.3.1", "2.4", "2.5", "2.7", "2.8.1"})
    void ackKeepsTheVersionAndControlIdAndPutsErrorsInTheRightFields(String version) throws Exception {
        var message = HapiContexts.current().getPipeParser().parse(
                "MSH|^~\\&|APP|FAC|DEST|FAC|20261112||ADT^A01^ADT_A01|CONTROL-1|P|" + version + "\rPID|1\r");
        var issue = IssueCodes.hapiIssue("PID-3", "Required element Identifier is missing");
        var report = Report.of("set/A01", "ADT^A01^ADT_A01", version, "CONTROL-1", "HAPI",
                new Report.SetRef("set", "", ""), List.of(issue));
        String er7 = Acks.acknowledge(message, report);
        var ack = HapiContexts.current().getPipeParser().parse(er7);
        assertEquals(version, ack.getVersion());
        Terser terser = new Terser(ack);
        assertEquals("AE", terser.get("/MSA-1"));
        assertEquals("CONTROL-1", terser.get("/MSA-2"));
        String location = version.equals("2.3.1") || version.equals("2.4") ? "/ERR-1-4-1" : "/ERR-3-1";
        assertEquals("101", terser.get(location));
    }

    @Test
    void refusalAckIsArWithoutInventingAnOriginalControlId() throws Exception {
        var ack = HapiContexts.current().getPipeParser().parse(Acks.reject("Profil inconnu"));
        Terser terser = new Terser(ack);
        assertEquals("AR", terser.get("/MSA-1"));
        assertEquals("N/A", terser.get("/MSA-2"));
        assertEquals("E", terser.get("/ERR-4"));
    }
}
