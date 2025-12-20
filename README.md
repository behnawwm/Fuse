# Fuse - Feature Toggle & Config KSP Processor

A Kotlin Symbol Processing (KSP) library that generates type-safe configuration classes from annotated data classes.

## Features

- **Type-safe configuration**: Generates nullable DTOs and non-nullable domain models
- **Centralized defaults**: Single source of truth for default values
- **Nested configurations**: Supports unlimited nesting depth
- **Null-safe mapping**: Automatic DTO to domain model conversion
- **Clean architecture**: Separates network and domain concerns

## Quick Start

1. Add the annotation to your configuration class:

```kotlin
@FuseFeatureToggle(title = "Smooth Zoom")
data class SmoothZoomFeatureToggle(
    val enabled: Boolean = false,
    val zoomLevel: Double = 1.0
)
```

2. Build your project - KSP will generate:
   - `SmoothZoomConfigDto` (nullable, network-facing)
   - `SmoothZoomConfig` (non-null, domain model)
   - Safe mapping functions
   - Feature enum entries

## Generated Code

The processor generates clean, type-safe code that handles null safety at the boundary between network and domain layers.

See [AGENTS.md](AGENTS.md) for detailed specifications and architecture documentation.
