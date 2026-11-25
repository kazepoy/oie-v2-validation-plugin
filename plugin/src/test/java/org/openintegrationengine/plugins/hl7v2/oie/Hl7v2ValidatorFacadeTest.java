/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.openintegrationengine.plugins.hl7v2.core.Report;
import org.openintegrationengine.plugins.hl7v2.core.Validator;

class Hl7v2ValidatorFacadeTest {

    @AfterEach
    void tearDown() {
        Hl7v2ValidatorFacade.close();
    }

    @Test
    void validatesWithBothHapiAndNistEnginesAndFormats() throws Exception {
        Hl7v2ValidatorFacade.configure(Path.of("demo/profiles-volume"));

        String settingsJson = Hl7v2ValidatorFacade.getSettingsJson();
        assertTrue(settingsJson.contains("profilesDir"));

        String setsJson = Hl7v2ValidatorFacade.listSetsJson();
        assertTrue(setsJson.contains("ihe-pam-fr-2.11.2"));

        String er7 = Files.readString(Path.of("examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7"));

        Validator.Result hapi = Hl7v2ValidatorFacade.validate(er7, "ihe-pam-fr-2.11.2", null, "hapi");
        assertEquals(Report.HAPI, hapi.report().engine());
        assertEquals(Report.SCHEMA, hapi.report().schema());
        assertFalse(hapi.ack().isBlank());

        Validator.Result nist = Hl7v2ValidatorFacade.validate(er7, "ihe-pam-fr-2.11.2", null, "nist");
        assertEquals(Report.NIST, nist.report().engine());
        assertFalse(nist.ack().isBlank());

        String reportJson = Hl7v2ValidatorFacade.validateToReportJson(er7, "ihe-pam-fr-2.11.2", null, "nist");
        assertTrue(reportJson.contains("\"schema\":\"oie.hl7v2.report/2\""));

        String text = Hl7v2ValidatorFacade.validateToText(er7, "ihe-pam-fr-2.11.2", null, "nist");
        assertTrue(text.contains("moteur     : NIST"));

        String fullJson = Hl7v2ValidatorFacade.validateToJson(er7, "ihe-pam-fr-2.11.2", null, "nist");
        assertTrue(fullJson.contains("\"report\""));
        assertTrue(fullJson.contains("\"ack\""));
    }

    @Test
    void negotiatesResponseFormatFromAcceptHeader() {
        assertEquals(MediaTypes.ResponseFormat.ER7, MediaTypes.responseFormat(null));
        assertEquals(MediaTypes.ResponseFormat.ER7, MediaTypes.responseFormat("*/*"));
        assertEquals(MediaTypes.ResponseFormat.ER7, MediaTypes.responseFormat("application/hl7-v2+er7"));
        assertEquals(MediaTypes.ResponseFormat.JSON, MediaTypes.responseFormat("application/json"));
        assertEquals(MediaTypes.ResponseFormat.TEXT, MediaTypes.responseFormat("text/plain"));
        assertEquals(MediaTypes.ResponseFormat.JSON, MediaTypes.responseFormat("text/plain;q=0.5, application/json;q=0.9"));
    }
}
