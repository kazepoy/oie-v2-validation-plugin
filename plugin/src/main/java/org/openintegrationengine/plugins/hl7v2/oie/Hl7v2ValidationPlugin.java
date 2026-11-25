/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package org.openintegrationengine.plugins.hl7v2.oie;

import java.nio.file.Path;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;

/**
 * OIE server plugin lifecycle entry point for HL7v2 Validation.
 */
public final class Hl7v2ValidationPlugin implements ServicePlugin {

    private static final Logger logger = LogManager.getLogger(Hl7v2ValidationPlugin.class);

    public static final String PROPERTY_PROFILES_DIR = "hl7v2.validation.profiles.dir";
    public static final String ENV_PROFILES_DIR = "OIE_HL7V2_PROFILES_DIR";
    public static final String DEFAULT_PROFILES_DIR = "extensions/hl7v2-validation/profiles";

    @Override
    public String getPluginPointName() {
        return Hl7v2ValidationServletInterface.PLUGIN_NAME;
    }

    @Override
    public void init(Properties properties) {
        configureFromProperties(properties);
    }

    @Override
    public void update(Properties properties) {
        configureFromProperties(properties);
    }

    @Override
    public Properties getDefaultProperties() {
        Properties props = new Properties();
        props.setProperty(PROPERTY_PROFILES_DIR, DEFAULT_PROFILES_DIR);
        return props;
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        String name = getPluginPointName();
        return new ExtensionPermission[] {
            new ExtensionPermission(
                    name,
                    Hl7v2ValidationServletInterface.PERMISSION_VALIDATE,
                    "List loaded HL7v2 profile sets, read validation settings, and validate HL7v2 messages using HAPI or NIST.",
                    new String[] { "getHl7v2Settings", "getHl7v2ProfileSets", "validateHl7v2Message", "validateHl7v2MessageAck" },
                    new String[0]),
            new ExtensionPermission(
                    name,
                    Hl7v2ValidationServletInterface.PERMISSION_MANAGE,
                    "Update the HL7v2 profiles directory and reload profile sets from the server filesystem.",
                    new String[] { "setHl7v2Settings", "reloadHl7v2ProfileSets" },
                    new String[0])
        };
    }

    @Override
    public void start() {
        logger.info("HL7v2 Validation plugin started (profiles dir: {}).", Hl7v2ValidatorFacade.getProfilesDir());
    }

    @Override
    public void stop() {
        Hl7v2ValidatorFacade.close();
        logger.info("HL7v2 Validation plugin stopped.");
    }

    private static void configureFromProperties(Properties properties) {
        String dir = System.getProperty(PROPERTY_PROFILES_DIR);
        if (dir == null || dir.isBlank()) {
            dir = System.getenv(ENV_PROFILES_DIR);
        }
        if (dir == null || dir.isBlank()) {
            dir = properties != null ? properties.getProperty(PROPERTY_PROFILES_DIR) : null;
        }
        if (dir == null || dir.isBlank()) {
            dir = DEFAULT_PROFILES_DIR;
        }
        Hl7v2ValidatorFacade.configure(Path.of(dir.trim()));
    }
}
