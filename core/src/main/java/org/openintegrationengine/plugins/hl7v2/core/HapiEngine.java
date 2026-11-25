package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.conf.check.DefaultValidator;
import ca.uhn.hl7v2.conf.parser.ProfileParser;
import ca.uhn.hl7v2.conf.spec.RuntimeProfile;
import ca.uhn.hl7v2.model.Message;

import java.util.Arrays;
import java.util.List;

/** Validation 2B ; profils et tables caches dans le jeu, validateur neuf a chaque appel. */
public final class HapiEngine implements Engine {
    @Override
    public String id() {
        return Report.HAPI;
    }

    @Override
    public boolean covers(ProductSet.Profile profile) {
        return profile.hasHapi();
    }

    public void prepare(ProductSet set) {
        set.profiles().values().stream().filter(this::covers).forEach(p -> profile(set, p));
    }

    private RuntimeProfile profile(ProductSet set, ProductSet.Profile profile) {
        return set.cache("hapi/profile/" + profile.id(), () -> {
                try {
                    return new ProfileParser(false).parse(set.source(profile.id()).orElseThrow());
                } catch (Exception e) {
                    throw new Refusal(ErrorCode.VALIDATION_FAILED, "Profil 2B illisible : " + profile.id(), e);
                }
            });
    }

    @Override
    public List<Report.Issue> check(ProductSet set, ProductSet.Profile profile, Message message, String er7) {
        try {
            RuntimeProfile runtime = profile(set, profile);
            DefaultValidator validator = new DefaultValidator();
            Tables.forSet(set, message.getVersion()).ifPresent(validator::setCodeStore);
            return Arrays.stream(validator.validate(message, runtime.getMessage()))
                    .map(error -> IssueCodes.hapiIssue(error.getLocation() == null ? "" : error.getLocation().toString(),
                            error.getMessageWithoutLocation())).toList();
        } catch (Refusal e) {
            throw e;
        } catch (Exception e) {
            throw new Refusal(ErrorCode.VALIDATION_FAILED, "Validation HAPI impossible : " + profile.id(), e);
        }
    }
}
