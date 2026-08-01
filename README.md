# Origins Modern UI 2.1.1

Graphical selection UI for Minecraft 1.21.1, NeoForge 21.1.241+, and NeoOrigins 2.2.21.

## Highlights

- Rating, impact, difficulty, favorites, filters and extensive sorting.
- Tags for Melee, Ranged, Magic, Water, Exploration, Economy, Tank and Mobility.
- Search across tags, names, descriptions, powers and addon namespaces.
- Selection history and Back navigation between Origin/Class/Background layers.
- Eased transitions, selected-card growth and smooth scrolling.
- Automatic support for NeoOrigins addon layers and datapack origins.

## Build

Run `BUILD-21.1.241.bat` or `gradlew.bat clean build` in IntelliJ's terminal.
The output is created in `build/libs/`.


## 2.3.0
Adds eased slide-and-fade transitions when moving forward or backward between selection layers. Controls are locked during motion to prevent accidental double clicks.


## 2.3.0 cinematic transitions
Layer changes now use directional depth slide, fade, subtle zoom/overshoot, vertical parallax, accent wipe, centre bloom, and short cinematic bars. Back plays the animation in reverse.
