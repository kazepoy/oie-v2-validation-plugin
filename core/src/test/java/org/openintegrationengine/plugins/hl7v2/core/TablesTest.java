package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TablesTest {
    @TempDir Path root;

    @Test
    void archiveTablesKeepTheSameCodesAndSkipEmptyUserDefinedTables() throws Exception {
        try (ProductSet set = ProductSet.open(Fixtures.PAM_ZIP)) {
            var codes = Tables.forSet(set, "2.5").orElseThrow();
            assertTrue(codes.isValidCode("HL70003", "A01"));
            assertFalse(codes.isValidCode("HL70003", "ZZZ"));
            assertTrue(Tables.forSet(set, "2.9").isEmpty());
            assertFalse(codes.knowsCodes("HL70300"));
            assertTrue(codes.isValidCode("HL70300", "ANY"));
        }
    }

    @Test
    void versionsAndLocalCodesAreIsolatedAndCachedOnlyForThisSet() throws Exception {
        Fixtures.profile(root, "A01", "ADT", "A01", "ADT_A01");
        Path tables = Files.createDirectories(root.resolve("hapi/tables"));
        for (String version : java.util.List.of("2.3.1", "2.5")) {
            Files.copy(Path.of("core/src/test/resources/hl7-tables/HL7Table-" + version + ".xml"),
                    tables.resolve("HL7Table-" + version + ".xml"));
        }
        Files.writeString(tables.resolve("HL7Table-local.xml"), """
                <Specification><hl7tables>
                  <hl7table id="0203"><tableElement code="INS"/></hl7table>
                  <hl7table id="0361"><tableElement code="DPI"/></hl7table>
                </hl7tables></Specification>
                """);
        try (ProductSet set = ProductSet.open(root)) {
            var older = Tables.forSet(set, "2.3.1").orElseThrow();
            var newer = Tables.forSet(set, "2.5").orElseThrow();
            assertFalse(older.isValidCode("0001", "A"));
            assertTrue(newer.isValidCode("0001", "A"));
            assertTrue(newer.isValidCode("0203", "INS"));
            assertTrue(newer.isValidCode("0203", "PI"));
            assertFalse(newer.isValidCode("0203", "ZZZ"));
            assertTrue(newer.isValidCode("0361", "DPI"));
            assertFalse(newer.isValidCode("0361", "OTHER"));
            assertSame(newer, Tables.forSet(set, "2.5").orElseThrow());
        }
    }

    @Test
    void noTablesMeansNoCodeCheckAndLegacyTablesRemainReadable() throws Exception {
        Path profile = Fixtures.profile(root, "A01", "ADT", "A01", "ADT_A01");
        try (ProductSet set = ProductSet.open(root)) {
            assertTrue(Tables.forSet(set, "2.5").isEmpty());
        }
        Files.move(profile, root.resolve("A01.xml"));
        Files.delete(root.resolve("hapi/messages"));
        Files.createDirectories(root.resolve("tables"));
        Files.copy(Path.of("core/src/test/resources/hl7-tables/HL7Table-2.5.xml"), root.resolve("tables/HL7Table-2.5.xml"));
        try (ProductSet set = ProductSet.open(root)) {
            assertTrue(set.isLegacyLayout());
            assertTrue(Tables.forSet(set, "2.5").orElseThrow().isValidCode("0003", "A01"));
        }
    }
}
