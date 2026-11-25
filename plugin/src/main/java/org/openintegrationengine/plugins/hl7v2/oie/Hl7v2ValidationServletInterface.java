/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;

/**
 * REST API of the HL7v2 Validation plugin under {@code /api/extensions/hl7v2-validation}.
 * Supports both HAPI conformance profile (Chapter 2B) and NIST IGAMT validation engines.
 */
@Path("/extensions/hl7v2-validation")
public interface Hl7v2ValidationServletInterface extends BaseServletInterface {

    String PLUGIN_NAME = "HL7v2 Validation";
    String PERMISSION_VALIDATE = "Validate HL7v2 messages";
    String PERMISSION_MANAGE = "Manage HL7v2 validation profiles";

    /**
     * Returns the active validation settings, including the configured profiles directory.
     */
    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    @MirthOperation(name = "getHl7v2Settings", display = "Get HL7v2 validation settings", permission = PERMISSION_VALIDATE)
    String getSettings() throws ClientException;

    /**
     * Updates the profiles directory setting ({@code {"profilesDir": "/path/to/profiles"}}) and reloads profile sets.
     */
    @POST
    @Path("/settings")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Produces(MediaType.APPLICATION_JSON)
    @MirthOperation(name = "setHl7v2Settings", display = "Update HL7v2 validation settings", permission = PERMISSION_MANAGE)
    String setSettings(String body) throws ClientException;

    /**
     * Lists all loaded profile sets with their version, hash, and profiles (including HAPI/NIST support).
     */
    @GET
    @Path("/sets")
    @Produces(MediaType.APPLICATION_JSON)
    @MirthOperation(name = "getHl7v2ProfileSets", display = "List HL7v2 profile sets", permission = PERMISSION_VALIDATE)
    String getProfileSets() throws ClientException;

    /**
     * Reloads profile sets from the configured directory on the OIE server.
     */
    @POST
    @Path("/sets/reload")
    @Produces(MediaType.APPLICATION_JSON)
    @MirthOperation(name = "reloadHl7v2ProfileSets", display = "Reload HL7v2 profile sets", permission = PERMISSION_MANAGE)
    String reloadProfileSets() throws ClientException;

    /**
     * Validates an HL7v2 ER7 message. Response format is negotiated via the HTTP {@code Accept} header
     * ({@code application/hl7-v2+er7}, {@code application/json}, {@code text/plain}; defaults to HL7v2 ACK ER7).
     */
    @POST
    @Path("/validate")
    @Consumes({ MediaTypes.HL7V2_ER7, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Produces({ MediaTypes.HL7V2_ER7, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @MirthOperation(name = "validateHl7v2Message", display = "Validate an HL7v2 message", permission = PERMISSION_VALIDATE)
    Response validateMessage(
            @QueryParam("set") String set,
            @QueryParam("profile") String profile,
            @QueryParam("engine") String engine,
            @HeaderParam("Accept") String accept,
            String body) throws ClientException;

    /**
     * Validates an HL7v2 ER7 message and returns the raw HL7 ACK ({@code AA}, {@code AE}, or {@code AR}).
     */
    @POST
    @Path("/validate/ack")
    @Consumes({ MediaTypes.HL7V2_ER7, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Produces({ MediaTypes.HL7V2_ER7, MediaType.TEXT_PLAIN })
    @MirthOperation(name = "validateHl7v2MessageAck", display = "Validate an HL7v2 message and return ACK", permission = PERMISSION_VALIDATE)
    Response validateMessageAck(
            @QueryParam("set") String set,
            @QueryParam("profile") String profile,
            @QueryParam("engine") String engine,
            String body) throws ClientException;
}
