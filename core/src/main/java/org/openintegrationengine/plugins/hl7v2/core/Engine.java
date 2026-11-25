package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.model.Message;

import java.util.List;

/** Un moteur, un rapport ; l'ER7 original reste disponible pour NIST. */
public interface Engine {
    String id();

    boolean covers(ProductSet.Profile profile);

    List<Report.Issue> check(ProductSet set, ProductSet.Profile profile, Message message, String er7);
}
