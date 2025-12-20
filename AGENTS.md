PROJECT CONTEXT: Fuse - Feature Toggle & Config KSP Processor
============================================================

GOAL
----
Build a Kotlin Symbol Processing (KSP)–based "config compiler" that:
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

ANNOTATION ROLE (METADATA ONLY)
-------------------------------
The annotation (FuseFeatureToggle) is METADATA ONLY.

Responsibilities:
- Feature discovery
- Title (human-readable)
- Default enabled state
- Optional naming overrides

The annotation MUST NOT:
- Contain business logic
- Contain backend keys

Example:

```kotlin
@FuseFeatureToggle(
    title = "Smooth Zoom",
    defaultEnabled = false,
    dtoName = "SmoothZoomConfigDto",    // Optional
    domainName = "SmoothZoomConfig",    // Optional
    enumName = "SmoothZoom"             // Optional
)
```

----------------------------------------------------------------

ANNOTATED CLASS (SINGLE SOURCE OF TRUTH)
----------------------------------------
The annotated class is the SINGLE SOURCE OF TRUTH.

It represents:
- Config schema
- All default values
- Enabled state
- Nested configuration structure

Rules for annotated class:
1. Must be a data class
2. Must define an `enabled: Boolean` property with default
3. Every property MUST have a default value
4. No nullable types allowed in schema
5. Nested configs must be inner data classes
6. No business logic allowed

Canonical example:

```kotlin
@FuseFeatureToggle(title = "Smooth Zoom")
data class SmoothZoomFeatureToggle(
    val enabled: Boolean = false,
    val zoom1: Double = 1.0,
    val zoom2: Zoom = Zoom()
) {
    data class Zoom(
        val zoom3: Double = 0.0,
        val zoom4: Double = 0.0
    )
}
```

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

DTO GENERATION RULES (IMPLEMENTED)
---------------------------------
- All fields nullable
- No default values
- Annotated with @Serializable
- Nested DTOs generated INSIDE parent DTO
- Mirrors backend uncertainty

Example:

```kotlin
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
```

----------------------------------------------------------------

DOMAIN MODEL RULES (IMPLEMENTED)
-------------------------------
- All fields non-nullable
- Safe for direct app usage
- Contains a single source of defaults

Example:

```kotlin
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
```

Nested domain models are TOP-LEVEL classes:

```kotlin
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
```

----------------------------------------------------------------

MAPPER RULES (IMPLEMENTED)
-------------------------
Mappers form the ONLY null-handling boundary.

Rules:
- Mapper receiver is nullable DTO
- Mapper return type is non-null domain
- DTO null → Domain.default
- Field null → Domain.default.field
- Nested DTO → delegated nested mapper

Example:

```kotlin
fun SmoothZoomConfigDto?.toSmoothZoomConfig(): SmoothZoomConfig =
    this?.let {
        SmoothZoomConfig(
            enabled = it.enabled ?: SmoothZoomConfig.default.enabled,
            zoom1  = it.zoom1  ?: SmoothZoomConfig.default.zoom1,
            zoom2  = it.zoom2.toZoomConfig()
        )
    } ?: SmoothZoomConfig.default
```

Nested mapper:

```kotlin
fun SmoothZoomConfigDto.ZoomConfigDto?.toZoomConfig(): ZoomConfig =
    this?.let {
        ZoomConfig(
            zoom3 = it.zoom3 ?: ZoomConfig.default.zoom3,
            zoom4 = it.zoom4 ?: ZoomConfig.default.zoom4
        )
    } ?: ZoomConfig.default
```

----------------------------------------------------------------

FEATURE ENUM (IMPLEMENTED)
-------------------------
FeatureToggles enum is metadata only.

- No backend keys
- No enabled state
- No defaults

Example:

```kotlin
enum class FeatureToggles(
    val title: String
) {
    SmoothZoom("Smooth Zoom"),
    PureCompose("Pure Compose"),
    SafetyChat("Safety Chat")
}
```

----------------------------------------------------------------

APPCONFIG (IMPLEMENTED)
----------------------
AppConfigDto (network-facing):
- Feature DTOs are nullable

AppConfig (domain):
- Feature domains are non-null

Mapping applies defaults automatically.

Example:

```kotlin
data class AppConfigDto(
    val smoothZoom: SmoothZoomConfigDto?,
    val pureCompose: PureComposeConfigDto?,
    val safetyChat: SafetyChatConfigDto?
)

data class AppConfig(
    val smoothZoom: SmoothZoomConfig,
    val pureCompose: PureComposeConfig,
    val safetyChat: SafetyChatConfig
)
```

Mapping:

```kotlin
fun AppConfigDto.toDomain(): AppConfig =
    AppConfig(
        smoothZoom = smoothZoom.toSmoothZoomConfig(),
        pureCompose = pureCompose.toPureComposeConfig(),
        safetyChat = safetyChat.toSafetyChatConfig()
    )
```

----------------------------------------------------------------

NESTED CONFIG SUPPORT (IMPLEMENTED)
----------------------------------
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

PROCESSOR ARCHITECTURE (REFACTORED)
----------------------------------
The KSP processor follows best practices:

**Core Components:**
- `FuseFeatureToggleProcessor`: Main processor orchestration
- `KspUtils`: Symbol processing utilities and validation
- `CodeGenerators`: Code generation logic
- `FeatureModels`: Data models for processing

**Processing Flow:**
1. Validate annotated classes (data class, enabled field, defaults)
2. Extract feature metadata and nested configurations
3. Generate individual files (DTOs, domains, nested domains)
4. Generate aggregate files (enum, app configs, mappers)

**Key Improvements:**
- Proper nested type resolution
- Centralized validation logic
- Modular code generation
- Better error handling and logging
- Deterministic output

----------------------------------------------------------------

PROCESSOR RESPONSIBILITIES SUMMARY
----------------------------------
The KSP processor:

- Treats annotated class as schema + defaults ✅
- Extracts:
  - field names ✅
  - types ✅
  - default values ✅
  - nesting structure ✅
- Generates:
  - nullable DTOs ✅
  - non-null domain models ✅
  - domain defaults ✅
  - null-safe mappers ✅
  - nested DTOs ✅
- Enforces:
  - presence of `enabled` ✅
  - presence of defaults ✅
- Never invents defaults ✅
- Never trusts backend nullability ✅
- Never duplicates default values ✅

This makes the processor a true CONFIG COMPILER.

----------------------------------------------------------------

USAGE EXAMPLES
--------------

**Simple Feature:**
```kotlin
@FuseFeatureToggle(title = "Pure Compose")
data class PureComposeFeatureToggle(
    val enabled: Boolean = false
)
```

**Complex Feature with Nested Config:**
```kotlin
@FuseFeatureToggle(title = "Smooth Zoom")
data class SmoothZoomFeatureToggle(
    val enabled: Boolean = false,
    val zoom1: Double = 1.0,
    val zoom2: Zoom = Zoom()
) {
    data class Zoom(
        val zoom3: Double = 0.0,
        val zoom4: Double = 0.0
    )
}
```

**Feature with Custom Naming:**
```kotlin
@FuseFeatureToggle(
    title = "Safety Chat",
    dtoName = "SafetyChatConfigDto",
    domainName = "SafetyChatConfig",
    enumName = "SafetyChat"
)
data class SafetyChatFeatureToggle(
    val enabled: Boolean = true,
    val maxMessages: Int = 100,
    val timeout: Long = 5000L
)
```

----------------------------------------------------------------

BUILD INTEGRATION
----------------
The processor is integrated as a KSP plugin:

```kotlin
// In build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":fuse-annotations"))
    ksp(project(":fuse-processor"))
}
```

Generated files are placed in `build/generated/ksp/main/kotlin/`

----------------------------------------------------------------

FINAL INVARIANTS CHECKLIST
-------------------------
- Annotated class defines all defaults ✅
- Enabled state is explicit ✅
- DTOs are nullable ✅
- Domain is non-null ✅
- Defaults are centralized ✅
- Nested configs work ✅
- No ambiguous imports ✅
- Deterministic output ✅
- Scales to large codebases ✅
- Follows KSP best practices ✅
- Proper error handling ✅
- Modular architecture ✅

----------------------------------------------------------------

END OF SPECIFICATION
============================================================
