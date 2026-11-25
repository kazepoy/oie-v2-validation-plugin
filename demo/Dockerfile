# syntax=docker/dockerfile:1.7
ARG BUSYBOX_IMAGE=busybox:1.37.0-glibc@sha256:42a9ff19af33811ee466b24c66384461d37c32dcc28d1d27d49b8aca816bd5ee
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-17

FROM curlimages/curl:8.21.0 AS downloads

# Web Support 1.3.0 (includes OIE Web Admin)
RUN curl --fail --silent --show-error --location --max-time 300 \
      --header 'Accept: application/octet-stream' \
      https://api.github.com/repos/gibson9583/oie-web-support-plugin/releases/assets/707054284 \
      --output /tmp/websupport.zip \
    && echo '69a6b2285000d6c7356aaa547746528fe0bf145ba9bdf5d595fb9a390aff54ff  /tmp/websupport.zip' | sha256sum -c -

# Role Based Access Control 1.3.0
RUN curl --fail --silent --show-error --location --max-time 300 \
      https://github.com/diridium-com/role-based-access-control/releases/download/v1.3.0/rbac-1.3.0.zip \
      --output /tmp/rbac.zip \
    && echo 'f540cc77ebfadda88147f07fce412caff41abf30ad302ddca61f0b3f422d0cfd  /tmp/rbac.zip' | sha256sum -c -

FROM ${BUSYBOX_IMAGE} AS plugins
COPY --from=downloads /tmp/*.zip /downloads/
RUN mkdir -p /extensions \
    && for zip in /downloads/*.zip; do unzip -q "$zip" -d /extensions; done \
    && chmod -R go-w /extensions

FROM ${MAVEN_IMAGE} AS builder
WORKDIR /build
COPY --from=oie-engine /opt/engine/server-lib/mirth-server.jar /engine/server-api.jar
COPY --from=oie-engine /opt/engine/server-lib/donkey/donkey-model.jar /engine/donkey-model.jar
COPY --from=oie-engine /opt/engine/server-lib/donkey/donkey-server.jar /engine/donkey-server.jar
COPY --from=oie-engine /opt/engine/server-lib/mirth-client-core.jar /engine/client-core.jar
COPY pom.xml ./
COPY core ./core
COPY plugin ./plugin
COPY lib ./lib
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp install:install-file -Dfile=/engine/server-api.jar \
      -DgroupId=com.mirth.connect -DartifactId=server-api -Dversion=4.7.0 -Dpackaging=jar \
    && mvn -B -ntp install:install-file -Dfile=/engine/donkey-model.jar \
      -DgroupId=com.mirth.connect -DartifactId=donkey-model -Dversion=4.7.0 -Dpackaging=jar \
    && mvn -B -ntp install:install-file -Dfile=/engine/donkey-server.jar \
      -DgroupId=com.mirth.connect -DartifactId=donkey-server -Dversion=4.7.0 -Dpackaging=jar \
    && mvn -B -ntp install:install-file -Dfile=/engine/client-core.jar \
      -DgroupId=com.mirth.connect -DartifactId=client-core -Dversion=4.7.0 -Dpackaging=jar \
    && mvn -B -ntp package -DskipTests \
    && mkdir /extensions && cd /extensions && jar xf /build/plugin/target/hl7v2-validation-*.zip

FROM oie-engine
COPY --from=plugins --chown=engine:engine /extensions/ /opt/engine/extensions/
COPY --from=builder --chown=engine:engine /extensions/ /opt/engine/extensions/
# Log plugin lifecycle at INFO and silence HAPI DefaultValidator value warnings.
RUN printf '\nlogger.hl7v2Validation.name = org.openintegrationengine.plugins.hl7v2\nlogger.hl7v2Validation.level = INFO\nlogger.hapiDefaultValidator.name = ca.uhn.hl7v2.conf.check.DefaultValidator\nlogger.hapiDefaultValidator.level = ERROR\n' >> conf/log4j2.properties
USER engine
LABEL org.opencontainers.image.title="OIE 4.7.0 HL7v2 Validation Demo - NOT FOR PRODUCTION USE"
