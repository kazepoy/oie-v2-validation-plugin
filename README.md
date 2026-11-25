# OIE HL7v2 Validation Plugin

Server plugin for **Open Integration Engine (OIE 4.7+)** providing HL7v2 message validation against **HAPI** (Chapter 2B conformance profiles) and **NIST** (IGAMT `Profile.xml` / `Constraints.xml` / `ValueSets.xml`) profile sets.

* Exposes REST endpoints under `/api/extensions/hl7v2-validation` with `Accept` content negotiation (`application/hl7-v2+er7` ACK v2, `application/json` report, `text/plain` summary).
* Exposes `Hl7v2ValidatorFacade` for direct calls from OIE channel JavaScript/Rhino scripts.

## Try it

### 1. Start the Docker demo (OIE on port `8445`)

```sh
cd demo
docker compose --profile build build oie-engine
docker compose up -d --build
```

OIE Web Admin: `https://127.0.0.1:8445/oie-webadmin/` (`admin` / `admin`).

### 2. Validate a sample message via REST

```sh
# HL7v2 ACK (AA / AE / AR) using NIST
curl -k -u admin:admin -X POST \
  -H "Content-Type: application/hl7-v2+er7" \
  -H "Accept: application/hl7-v2+er7" \
  --data-binary @examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7 \
  "https://127.0.0.1:8445/api/extensions/hl7v2-validation/validate?set=ihe-pam-fr-2.11.2&engine=nist"

# JSON report using HAPI
curl -k -u admin:admin -X POST \
  -H "Content-Type: application/hl7-v2+er7" \
  -H "Accept: application/json" \
  --data-binary @examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7 \
  "https://127.0.0.1:8445/api/extensions/hl7v2-validation/validate?set=ihe-pam-fr-2.11.2&engine=hapi"
```

Ready-to-run HTTP requests are available in [examples/validation.http](examples/validation.http).

## Calling from an OIE Channel (JavaScript / Rhino)

In a **Filter**, **Transformer**, or **Response Transformer**, call `Hl7v2ValidatorFacade` directly without HTTP overhead:

```javascript
var Facade = Packages.org.openintegrationengine.plugins.hl7v2.oie.Hl7v2ValidatorFacade;

// 1. Raw ER7 from the inbound connector (or serialize XML 'msg' back to ER7 if modified)
var er7 = connectorMessage.getRawData();
// If validating after E4X transformations:
// var er7 = SerializerFactory.getSerializer('HL7V2').fromXML(msg.toString());

// 2. Validate (profile = null for automatic MSH-9 resolution; engine = 'nist' or 'hapi')
var result = Facade.validate(er7, 'ihe-pam-fr-2.11.2', null, 'nist');
var report = result.report();

if (!report.valid()) {
    logger.warn('HL7v2 validation failed (' + report.errorCount() + ' errors): ' + report.toText());
    // Store the generated AE ACK in channelMap for the Source Response
    channelMap.put('validationAck', result.ack());
    return false; // e.g. reject in Filter
}

// Or get JSON / text / ACK directly:
// var jsonReport = Facade.validateToReportJson(er7, 'ihe-pam-fr-2.11.2', null, 'hapi');
// var textReport = Facade.validateToText(er7, 'ihe-pam-fr-2.11.2', null, 'nist');
// var ack = Facade.validateToAck(er7, 'ihe-pam-fr-2.11.2', null, 'nist');
```

## Profiles Directory Configuration

The directory containing HL7v2 profile sets (`.zip` archives or extracted folders) can be configured via any of the following (in priority order):

1. **JVM system property**: `-Dhl7v2.validation.profiles.dir=/path/to/profiles`
2. **Environment variable**: `OIE_HL7V2_PROFILES_DIR=/path/to/profiles`
3. **OIE Plugin Properties / REST API**:
   * `GET /api/extensions/hl7v2-validation/settings`
   * `POST /api/extensions/hl7v2-validation/settings` with `{"profilesDir": "/path/to/profiles"}` (persisted in OIE extension properties and reloaded immediately)
4. **Default**: `extensions/hl7v2-validation/profiles`

## Build from source

```sh
mvn package
```

Produces the installable OIE extension archive at `plugin/target/hl7v2-validation-0.1.0.zip`.

## Documentation

* [Feasibility study](doc/FEASIBILITY.md)
* [Demo guide](demo/README.md)
* [Third-party licenses](THIRD-PARTY-LICENSES.md)
