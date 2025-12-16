PROJECT CONTEXT: Tapsi Feature Toggle & Config KSP Processor
============================================================

GOAL
----
Build a Kotlin Symbol Processing (KSP)–based “config compiler” that:
- Treats annotated classes as schema + defaults
- Generates nullable DTOs (network-facing)
- Generates non-nullable domain models (app-facing)
- Centralizes defaults in domain companion objects
- Generates safe mappers (DTO? -> Domain)
- Supports nested configuration models
- Avoids ambiguous imports
- Is deterministic, scalable, and null-safe
- Is suitable for Android / Web / iOS parity later

This system replaces ad-hoc feature toggles with a typed, generated configuration platform.

----------------------------------------------------------------

ANNOTATION ROLE
---------------
The annotation (TapsiFeatureToggle) is METADATA ONLY.

Responsibilities:
- Feature discovery
- Title (human-readable)
- Optional naming overrides

The annotation MUST NOT:
- Contain defaults
- Contain enabled state
- Contain business logic
- Contain backend keys

Example (conceptual):

@TapsiFeatureToggle(
  title = "Smooth Zoom",
  dtoName?, domainName?, enumName?
)

----------------------------------------------------------------

ANNOTATED CLASS (CRITICAL)
-------------------------
The annotated class is the SINGLE SOURCE OF TRUTH.

It represents:
- Config schema
- All default values
- Enabled state
- Nested configuration structure

Rules for annotated class:
1. Must be a data class
2. Must define an `enabled: Boolean` property
3. Every property MUST have a default value
4. No nullable types allowed in schema
5. Nested configs must be inner data classes
6. No business logic allowed

Canonical example:

@TapsiFeatureToggle(title = "Smooth Zoom")
data class SmoothZoom(
    val enabled: Boolean = false,
    val zoom1: Double = 1.0,
    val zoom2: Zoom = Zoom()
) {
    data class Zoom(
        val zoom3: Double = 0.0,
        val zoom4: Double = 0.0
    )
}

----------------------------------------------------------------

NAMING RULES (DEFAULTS)
----------------------
Given schema name = FeatureName

DTO:
- FeatureNameConfigDto

Domain:
- FeatureNameConfig

Enum:
- FeatureName

Nested schemas follow the same pattern.

Naming overrides are optional via annotation.
If empty → defaults apply.

----------------------------------------------------------------

GENERATED OUTPUT OVERVIEW
-------------------------

For each annotated schema, KSP generates:

1. DTO (nullable, Serializable, nested DTOs allowed)
2. Domain model (non-nullable)
3. Domain default (companion object)
4. Mapper (DTO? -> Domain)
5. Feature enum entry
6. AppConfigDto (nullable feature DTOs)
7. AppConfig (non-null feature domains)

----------------------------------------------------------------

DTO GENERATION RULES
-------------------
- All fields nullable
- No default values
- Annotated with @Serializable
- Nested DTOs generated INSIDE parent DTO
- Mirrors backend uncertainty

Example:

@Serializable
data class SmoothZoomConfigDto(
    val enabled: Boolean?,
    val zoom1: Double?,
    val zoom2: ZoomConfigDto?
) {
    @Serializable
    data class ZoomConfigDto(
        val zoom3: Double?,
        val zoom4: Double?
    )
}

----------------------------------------------------------------

DOMAIN MODEL RULES
-----------------
- All fields non-nullable
- Safe for direct app usage
- Contains a single source of defaults

Example:

data class SmoothZoomConfig(
    val enabled: Boolean,
    val zoom1: Double,
    val zoom2: ZoomConfig
) {
    companion object {
        val default = SmoothZoomConfig(
            enabled = false,
            zoom1 = 1.0,
            zoom2 = ZoomConfig.default
        )
    }
}

Nested domain models are TOP-LEVEL classes:

data class ZoomConfig(
    val zoom3: Double,
    val zoom4: Double
) {
    companion object {
        val default = ZoomConfig(
            zoom3 = 0.0,
            zoom4 = 0.0
        )
    }
}

----------------------------------------------------------------

MAPPER RULES (CRITICAL)
----------------------
Mappers form the ONLY null-handling boundary.

Rules:
- Mapper receiver is nullable DTO
- Mapper return type is non-null domain
- DTO null → Domain.default
- Field null → Domain.default.field
- Nested DTO → delegated nested mapper

Example:

fun SmoothZoomConfigDto?.toSmoothZoomConfig(): SmoothZoomConfig =
    this?.let {
        SmoothZoomConfig(
            enabled = it.enabled ?: SmoothZoomConfig.default.enabled,
            zoom1  = it.zoom1  ?: SmoothZoomConfig.default.zoom1,
            zoom2  = it.zoom2.toZoomConfig()
        )
    } ?: SmoothZoomConfig.default

Nested mapper:

fun SmoothZoomConfigDto.ZoomConfigDto?.toZoomConfig(): ZoomConfig =
    this?.let {
        ZoomConfig(
            zoom3 = it.zoom3 ?: ZoomConfig.default.zoom3,
            zoom4 = it.zoom4 ?: ZoomConfig.default.zoom4
        )
    } ?: ZoomConfig.default

----------------------------------------------------------------

FEATURE ENUM
------------
FeatureToggles enum is metadata only.

- No backend keys
- No enabled state
- No defaults

Example:

enum class FeatureToggles(
    val title: String
) {
    SmoothZoom("Smooth Zoom")
}

----------------------------------------------------------------

APPCONFIG
---------
AppConfigDto (network-facing):
- Feature DTOs are nullable

AppConfig (domain):
- Feature domains are non-null

Mapping applies defaults automatically.

Example:

data class AppConfigDto(
    val smoothZoom: SmoothZoomConfigDto?
)

data class AppConfig(
    val smoothZoom: SmoothZoomConfig
)

Mapping:

fun AppConfigDto.toDomain(): AppConfig =
    AppConfig(
        smoothZoom = smoothZoom.toSmoothZoomConfig()
    )

----------------------------------------------------------------

NESTED CONFIG SUPPORT
--------------------
Nested schemas are handled recursively.

Mechanism:
- Inner data classes detected via KSP
- Each nested schema treated as independent config
- Nested DTOs generated inside parent DTO
- Nested domain models generated as top-level classes
- Nested defaults referenced (never inlined)
- Nested mappers generated and composed

Supports unlimited depth.

----------------------------------------------------------------

PROCESSOR RESPONSIBILITIES SUMMARY
----------------------------------
The KSP processor must:

- Treat annotated class as schema + defaults
- Extract:
  - field names
  - types
  - default values
  - nesting structure
- Generate:
  - nullable DTOs
  - non-null domain models
  - domain defaults
  - null-safe mappers
  - nested DTOs
- Enforce:
  - presence of `enabled`
  - presence of defaults
- Never invent defaults
- Never trust backend nullability
- Never duplicate default values

This makes the processor a CONFIG COMPILER.

----------------------------------------------------------------

NON-GOALS (INTENTIONALLY EXCLUDED)
---------------------------------
- Business logic in annotation
- Default values in DTO
- Nullable domain models
- Manual mapping
- Runtime null checks
- Backend key ownership
- Feature enablement logic inside enum

----------------------------------------------------------------

FINAL INVARIANTS CHECKLIST
-------------------------
- Annotated class defines all defaults ✔
- Enabled state is explicit ✔
- DTOs are nullable ✔
- Domain is non-null ✔
- Defaults are centralized ✔
- Nested configs work ✔
- No ambiguous imports ✔
- Deterministic output ✔
- Scales to large codebases ✔

----------------------------------------------------------------

END OF CONTEXT
============================================================

