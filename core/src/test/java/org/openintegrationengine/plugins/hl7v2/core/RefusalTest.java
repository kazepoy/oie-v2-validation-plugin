package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RefusalTest {

    @ParameterizedTest
    @CsvSource({
            "EMPTY_BODY,400", "UNREADABLE_MESSAGE,400", "UNREADABLE_MSH,400", "SET_REQUIRED,400",
            "INVALID_PARAMETER,400", "UNKNOWN_SET,404", "UNKNOWN_PROFILE,404", "NO_PROFILE_FOR_MESSAGE,404",
            "AMBIGUOUS_PROFILE,409", "ENGINE_NOT_AVAILABLE,422", "VALIDATION_FAILED,422",
            "CONFLICT,409", "INVALID_SET,422", "FORBIDDEN,403"
    })
    void refusalHasAStableMachineCodeAndHttpStatus(ErrorCode code, int status) {
        var refusal = new Refusal(code, "Explication humaine modifiable");
        assertEquals(code, refusal.code());
        assertEquals(status, refusal.status());
        assertEquals(List.of(), refusal.candidates());
    }

    @Test
    void ambiguityKeepsAnImmutableCopyOfCandidates() {
        var candidates = new ArrayList<>(List.of("ADT_A01", "ADT_A01_LOCAL"));
        var refusal = new Refusal(ErrorCode.AMBIGUOUS_PROFILE, "Plusieurs profils", candidates, null);
        candidates.clear();
        assertEquals(List.of("ADT_A01", "ADT_A01_LOCAL"), refusal.candidates());
        assertThrows(UnsupportedOperationException.class, () -> refusal.candidates().clear());
    }
}
