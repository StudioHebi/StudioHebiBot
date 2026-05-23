plugins {
    id("java")
    id("io.github.yvancywan.anvilcord") version "0.14.0"
}

group = "com.studio-hebi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.yvancywan:anvilcord-discord:0.7.0") {
        isTransitive = false
    }

    // These are AnvilCord Official Plugins, hence only need to be here for the runtime
    runtimeOnly("io.github.yvancywan:announcement:0.2.0")
    runtimeOnly("io.github.yvancywan:dice-roll:0.2.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
