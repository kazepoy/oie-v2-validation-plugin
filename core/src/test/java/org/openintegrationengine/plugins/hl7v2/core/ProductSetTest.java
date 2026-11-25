package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductSetTest {
    @TempDir Path root;

    @Test
    void readsTheDeliveredArchiveAndAttachesNistMessages() {
        try (ProductSet set = ProductSet.open(Fixtures.PAM_ZIP)) {
            assertEquals(35, set.profiles().size());
            var profile = set.profile(Fixtures.A01).orElseThrow();
            assertTrue(profile.hasHapi());
            assertTrue(profile.hasNist());
            assertEquals("ADT^A01^ADT_A01", profile.key());
            assertEquals("", set.version(), "L'archive historique livree ne porte pas de set.yaml");
            assertTrue(set.hash().matches("sha256:[0-9a-f]{64}"));
            assertTrue(set.source(Fixtures.A01).orElseThrow().contains("HL7v2xConformanceProfile"));
            assertThrows(UnsupportedOperationException.class, () -> set.profiles().clear());
            assertThrows(UnsupportedOperationException.class, () -> set.arbitration().clear());
        }
    }

    @Test
    void twoReadersOfTheSameArchiveHaveIndependentLifetimes() {
        try (ProductSet first = ProductSet.open(Fixtures.PAM_ZIP)) {
            try (ProductSet second = ProductSet.open(Fixtures.PAM_ZIP)) {
                assertEquals(first.profiles().keySet(), second.profiles().keySet());
            }
            assertTrue(first.source(Fixtures.A01).isPresent());
        }
    }

    @Test
    void directoryMetadataAndCachesAreReloadedWithTheSet() throws Exception {
        Fixtures.profile(root, "A01", "ADT", "A01", "ADT_A01");
        Files.writeString(root.resolve("set.yaml"), "version: \"3\"\nproducts:\n  modelHash: \"sha256:" + "a".repeat(64) + "\"\n");
        try (ProductSet set = ProductSet.open(root)) {
            assertEquals("3", set.version());
            assertEquals("sha256:" + "a".repeat(64), set.hash());
            assertEquals("old", set.cache("test", () -> "old"));
        }
        try (ProductSet set = ProductSet.open(root)) {
            assertEquals("new", set.cache("test", () -> "new"));
        }
    }
}
