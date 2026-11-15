# Throwing Discs (Fabric 1.21.1)

Java port of the Bedrock addon "Throwing Discs" by Ace: copper, iron, golden, diamond and netherite
discs that fly straight, ricochet off hard blocks, pierce mobs and return like a boomerang.

## Build
Requires JDK 21.

    ./gradlew build        # jar in build/libs/throwing_discs-1.2.0.jar
    ./gradlew runClient    # test in a dev client

Install the jar in `mods/` together with Fabric Loader and Fabric API 0.116.10+1.21.1.

## Controls
Right-click throws the disc. The off-hand works natively in Java: with a disc in the off-hand,
right-click with an empty main hand (or any item without its own right-click use).
