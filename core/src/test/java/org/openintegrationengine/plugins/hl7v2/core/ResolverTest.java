package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolverTest {
    @TempDir Path root;

    @Test
    void exactMatchThenEventThenOwnerOfTheStructure() throws Exception {
        Fixtures.profile(root, "A01", "ADT", "A01", "ADT_A01");
        Fixtures.profile(root, "A08", "ADT", "A08", "ADT_A01");
        try (ProductSet set = ProductSet.open(root)) {
            assertEquals("A08", Resolver.resolve(set, "ADT", "A08", "ADT_A01").id());
            assertEquals("A08", Resolver.resolve(set, "ADT", "A08", "OTHER").id());
            assertEquals("A01", Resolver.resolve(set, "ADT", "A99", "ADT_A01").id());
            assertEquals(ErrorCode.NO_PROFILE_FOR_MESSAGE, assertThrows(Refusal.class,
                    () -> Resolver.resolve(set, "ORU", "R01", "ORU_R01")).code());
        }
    }

    @Test
    void ambiguityHasCandidatesAndCanBeArbitrated() throws Exception {
        Fixtures.profile(root, "A01", "ADT", "A01", "ADT_A01");
        Fixtures.profile(root, "A01_LOCAL", "ADT", "A01", "ADT_A01");
        try (ProductSet set = ProductSet.open(root)) {
            Refusal e = assertThrows(Refusal.class, () -> Resolver.resolve(set, "ADT", "A01", "ADT_A01"));
            assertEquals(ErrorCode.AMBIGUOUS_PROFILE, e.code());
            assertEquals(List.of("A01", "A01_LOCAL"), e.candidates());
            assertEquals(1, Resolver.ambiguous(set).size());
        }
        Files.writeString(root.resolve("resolution.yaml"), "ambiguous:\n  \"ADT^A01^ADT_A01\": A01_LOCAL\n");
        try (ProductSet set = ProductSet.open(root)) {
            assertEquals("A01_LOCAL", Resolver.resolve(set, "ADT", "A01", "ADT_A01").id());
            assertTrue(Resolver.ambiguous(set).isEmpty());
        }
    }

    @Test
    void ackWildcardArbitrationUsesTheDeclaredKey() throws Exception {
        Fixtures.profile(root, "PEC/ACK_ALL", "ACK", "ALL", "ACK");
        Fixtures.profile(root, "PDC/ACK_ALL", "ACK", "ALL", "ACK");
        Files.writeString(root.resolve("resolution.yaml"), "ambiguous:\n  \"ACK^ALL^ACK\": PEC/ACK_ALL\n");
        try (ProductSet set = ProductSet.open(root)) {
            assertEquals("PEC/ACK_ALL", Resolver.resolve(set, "ACK", "A01", "ACK").id());
        }
    }
}
