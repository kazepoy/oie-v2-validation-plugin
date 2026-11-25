/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;

import org.openintegrationengine.plugins.hl7v2.core.Acks;
import org.openintegrationengine.plugins.hl7v2.core.Refusal;
import org.openintegrationengine.plugins.hl7v2.core.Validator;

/**
 * REST servlet implementing {@link Hl7v2ValidationServletInterface}.
 */
public class Hl7v2ValidationServlet extends MirthServlet implements Hl7v2ValidationServletInterface {

    public Hl7v2ValidationServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_NAME);
    }

    @Override
    public String getSettings() throws ClientException {
        try {
            return Hl7v2ValidatorFacade.getSettingsJson();
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    @Override
    public String setSettings(String body) throws ClientException {
        try {
            String dir;
            if (body != null && body.trim().startsWith("{")) {
                Map<String, Object> json = Hl7v2ValidatorFacade.parseJsonMap(body);
                dir = asString(json.get("profilesDir"));
            } else {
                dir = body;
            }
            if (dir == null || dir.isBlank()) {
                throw new ClientException("profilesDir is required.");
            }
            String trimmed = dir.trim();
            Hl7v2ValidatorFacade.configure(Path.of(trimmed));
            Properties props = new Properties();
            props.setProperty(Hl7v2ValidationPlugin.PROPERTY_PROFILES_DIR, trimmed);
            ControllerFactory.getFactory().createExtensionController().setPluginProperties(PLUGIN_NAME, props);
            return Hl7v2ValidatorFacade.getSettingsJson();
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    @Override
    public String getProfileSets() throws ClientException {
        try {
            return Hl7v2ValidatorFacade.listSetsJson();
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    @Override
    public String reloadProfileSets() throws ClientException {
        try {
            Hl7v2ValidatorFacade.reload();
            return Hl7v2ValidatorFacade.listSetsJson();
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    @Override
    public Response validateMessage(String set, String profile, String engine, String accept, String body)
            throws ClientException {
        ValidationInput input = parseInput(set, profile, engine, body);
        MediaTypes.ResponseFormat responseFormat = MediaTypes.responseFormat(accept);
        try {
            Validator.Result result = Hl7v2ValidatorFacade.validate(
                    input.message(), input.set(), input.profile(), input.engine());
            String payload = switch (responseFormat) {
                case ER7 -> result.ack();
                case TEXT -> result.report().toText();
                case JSON -> Hl7v2ValidatorFacade.reportToJson(result.report());
            };
            return Response.ok(payload, MediaTypes.contentTypeOf(responseFormat)).build();
        } catch (Refusal r) {
            return refusalResponse(r, responseFormat);
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    @Override
    public Response validateMessageAck(String set, String profile, String engine, String body) throws ClientException {
        ValidationInput input = parseInput(set, profile, engine, body);
        try {
            String ack = Hl7v2ValidatorFacade.validateToAck(
                    input.message(), input.set(), input.profile(), input.engine());
            return Response.ok(ack, MediaTypes.HL7V2_ER7_UTF8).build();
        } catch (Refusal r) {
            return refusalResponse(r, MediaTypes.ResponseFormat.ER7);
        } catch (Exception e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    private static Response refusalResponse(Refusal refusal, MediaTypes.ResponseFormat format) {
        String entity = switch (format) {
            case ER7 -> Acks.reject(refusal.getMessage());
            case TEXT -> refusal.code().name() + ": " + refusal.getMessage() + "\n";
            case JSON -> Hl7v2ValidatorFacade.refusalToJson(refusal);
        };
        return Response.status(refusal.status())
                .entity(entity)
                .type(MediaTypes.contentTypeOf(format))
                .build();
    }

    private static ValidationInput parseInput(String set, String profile, String engine, String body)
            throws ClientException {
        if (body != null && body.trim().startsWith("{")) {
            try {
                Map<String, Object> json = Hl7v2ValidatorFacade.parseJsonMap(body);
                String s = firstNonBlank(asString(json.get("set")), set);
                String p = firstNonBlank(asString(json.get("profile")), profile);
                String e = firstNonBlank(asString(json.get("engine")), engine);
                String msg = asString(json.get("message"));
                return new ValidationInput(s, p, e, msg);
            } catch (Exception e) {
                throw new ClientException("Invalid JSON validation payload: " + e.getMessage(), e);
            }
        }
        return new ValidationInput(set, profile, engine, body);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private record ValidationInput(String set, String profile, String engine, String message) {
    }
}
