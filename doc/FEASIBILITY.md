# Feasibility Study — OIE HL7v2 Validation Plugin (Conformance Profile / NIST)

This document summarizes the architectural and technical options for adding **Conformance Profile** or **NIST** HL7v2 validation to **Open Integration Engine (OIE)**.

---

## 1. Overall Feasibility

**Yes, this is feasible.** OIE relies on a modular extension architecture inherited from Mirth Connect that allows adding Java plugins via a `plugin.xml` descriptor.

However, OIE does not provide a mechanism to dynamically inject new configuration properties or validation hooks into the built-in `HL7V2` DataType from an external plugin without modifying its source code. Selecting the right integration pattern is therefore key.

---

## 2. Integration Approaches in OIE

| Criteria | Option A: Dedicated DataType (`HL7V2-NIST`) | Option B: Service / Utility Plugin (Recommended) | Option C: Fork Built-in `HL7V2` DataType |
| :--- | :--- | :--- | :--- |
| **Approach** | Create a new DataType (`DataTypeServerPlugin` + `DataTypeClientPlugin`) wrapping `ER7Serializer` and validation. | Keep the native `HL7V2` DataType and expose a Java validator callable from *Filters* / *Transformers* / *Code Templates*. | Modify OIE's core `HL7V2` module directly. |
| **UI Integration** | Directly inside the channel *Set Data Types* dialog. | Called from channel scripts (+ optional admin panel to manage profiles). | Directly inside *Set Data Types*. |
| **Flow & ACK Control** | Limited: a parsing-level failure puts the message in an error state before reaching the *Transformer*. | Full control: inspect the structured report to route, log, or build custom `AE`/`AR` ACKs with `ERR` segments. | Full control, at a high maintenance cost. |
| **Complexity / Maintenance** | High (wrapping/delegating `ER7Serializer`, `BatchAdaptor`, supporting both Desktop Swing & Web Admin UIs, utilities looking up `"HL7V2"` such as `ACKGenerator`). | Low to moderate, decoupled from core DataType updates and compatible with both Desktop & Web Admin. | Very high (requires rebasing/rebuilding on every OIE upgrade). |

---

## 3. Java HL7v2 Validation Engines

### A. NIST Engine (IGAMT / `gvt-core`)
* **Profile Format:** XML bundles exported from **NIST IGAMT** (`PROFILE.xml`, `CONSTRAINTS.xml`, `VALUESETS.xml`).
* **Libraries:** `usnistgov/gvt-core` (`gov.nist.healthcare` / `gov.nist.hit`) or a simplified wrapper such as `gov.cdc:lib-hl7v2-nist-validator` (`ProfileManager`, `NistReport`).
* **Strengths:** Industry standard for strict conformance testing (cardinalities, conditional/co-occurrence constraints, value sets, IHE/national implementation guides).
* **Caveat:** Heavy transitive Java dependency tree.

### B. HAPI Engine (`ca.uhn.hl7v2`)
* **Profile Format:** Classic HL7 XML conformance profiles (e.g., *Messaging Workbench* / MWB).
* **Core Classes:** `ProfileParser` (`RuntimeProfile`) and `DefaultValidator`.
* **Strengths:** Lightweight and straightforward to embed.
* **Limitations:** Does not natively support modern NIST IGAMT exports (`CONSTRAINTS.xml`, `VALUESETS.xml`) and offers less expressive constraint rules.

---

## 4. Key Technical Considerations

1. **Dependency Isolation (Classpath Conflicts):**
   NIST libraries pull in many dependencies (XML parsers, Jackson, Spring, etc.) that may conflict with versions bundled in OIE. **Shading/relocating** dependencies via Maven (`maven-shade-plugin`) or isolating the plugin classloader is strongly recommended.
2. **Profile Caching:**
   Parsing and compiling XML profile bundles (`PROFILE`, `CONSTRAINTS`, `VALUESETS`) is expensive. Compiled profiles must be **cached in memory** (keyed by profile ID/version) rather than reloaded per message.
3. **Message Format (ER7 vs. OIE Internal XML):**
   OIE converts HL7v2 messages into an internal XML representation (`<HL7Message>`) for E4X manipulation in transformers, whereas NIST and HAPI validators expect **raw ER7 text** (`\r` segment delimiters). Outbound (post-transformation) validation requires serializing back to ER7 first.
4. **Dual OIE Admin UIs:**
   OIE supports both the legacy Desktop Administrator (Java Swing) and the newer Web Administrator (React/TypeScript). Starting with a utility/service plugin (Option B) avoids building and maintaining DataType configuration panels for both UIs upfront.

---

## 5. Recommended POC Roadmap

1. **Validate the target engine** (NIST IGAMT vs. HAPI/MWB) in a standalone Java module against real sample messages and profiles.
2. **Expose the validator in OIE** using **Option B** (server plugin exposing a cached validation service/helper) to test channel behavior and `AE`/`AR` ACK generation.
3. **Re-evaluate Option A** only if native *Set Data Types* ergonomics are strictly required over a global *Code Template*.
