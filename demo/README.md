# OIE HL7v2 Validation Demo

Demo environment running **Open Integration Engine 4.7.0** with the `hl7v2-validation` plugin and a mounted `profiles-volume/` containing the extracted **IHE PAM France 2.11.2** (`ihe-pam-fr-2.11.2/`) profile set (both HAPI and NIST).

## Prerequisites & Start

```sh
cd demo

# Build the plugin image and start PostgreSQL + OIE
docker compose up -d --build
```

OIE Web Admin is available at `https://127.0.0.1:8445/oie-webadmin/` (default credentials: `admin` / `admin`).

## Testing the REST API

List loaded profile sets:

```sh
curl -k -u admin:admin \
  https://127.0.0.1:8445/api/extensions/hl7v2-validation/sets
```

Validate an IHE PAM FR message with **NIST** or **HAPI** (content negotiation via `Accept` header):

```sh
# 1. Default / Accept: application/hl7-v2+er7 -> returns HL7v2 ACK (AA / AE / AR)
curl -k -u admin:admin -X POST \
  -H "Content-Type: application/hl7-v2+er7" \
  -H "Accept: application/hl7-v2+er7" \
  --data-binary @../examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7 \
  "https://127.0.0.1:8445/api/extensions/hl7v2-validation/validate?set=ihe-pam-fr-2.11.2&engine=nist"

# 2. Accept: application/json -> returns structured JSON report
curl -k -u admin:admin -X POST \
  -H "Content-Type: application/hl7-v2+er7" \
  -H "Accept: application/json" \
  --data-binary @../examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7 \
  "https://127.0.0.1:8445/api/extensions/hl7v2-validation/validate?set=ihe-pam-fr-2.11.2&engine=hapi"

# 3. Accept: text/plain -> returns human-readable text summary
curl -k -u admin:admin -X POST \
  -H "Content-Type: application/hl7-v2+er7" \
  -H "Accept: text/plain" \
  --data-binary @../examples/ihe-pam-fr-2.11.2/adt-a01-admission-pam-fr.er7 \
  "https://127.0.0.1:8445/api/extensions/hl7v2-validation/validate?set=ihe-pam-fr-2.11.2&engine=nist"
```
