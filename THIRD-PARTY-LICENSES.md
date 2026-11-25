# Third-Party Licenses

This plugin bundles or depends on the following third-party libraries:

| Component | Artifact(s) | License | Notes |
| :--- | :--- | :--- | :--- |
| **NIST HL7v2 Validation Engine** | `gov.nist:hl7-v2-validation:1.7.3`, `gov.nist:hl7-v2-parser:1.7.3`, `gov.nist:hl7-v2-profile:1.7.3`, `gov.nist:xml-util:2.1.0`, `gov.nist.hit:hl7-v2-schemas:1.7.2` | **NIST Software License / U.S. Government Public Domain** (17 U.S.C. § 105) | Developed by NIST (U.S. Federal Government). Public domain in the U.S.; perpetual, worldwide, non-exclusive, royalty-free license outside the U.S., provided "AS IS" without warranty. |
| **HL7 Tools Validation Report** | `com.github.hl7-tools:validation-report:1.2.0` | **Apache-2.0 / NIST-derived** | Used by the NIST validation engine for structured report generation. |
| **HAPI HL7v2** | `ca.uhn.hapi:hapi-base:2.6.0`, `ca.uhn.hapi:hapi-structures-v2*:2.6.0` | **MPL-1.1 / GPL-2.0 / LGPL-2.1** (tri-license) | Used under MPL-compatible terms for ER7 parsing, Chapter 2B conformance profile validation, and ACK generation. |
| **XOM** | `xom:xom:1.3.7` | **LGPL-2.1** | Transitive dependency of NIST `hl7-v2-validation`. Bundled unmodified in the shaded plugin JAR. |
| **Scala Standard Library & XML** | `org.scala-lang:scala-library:2.13.10`, `org.scala-lang.modules:scala-xml_2.13:1.3.0` | **Apache-2.0** | Runtime dependency of the NIST validation engine. |
| **SnakeYAML / Typesafe Config / Apache HttpComponents / Commons** | `org.yaml:snakeyaml`, `com.typesafe:config`, `org.apache.httpcomponents:*`, `commons-*` | **Apache-2.0** | Configuration, YAML parsing, and utility dependencies. |
