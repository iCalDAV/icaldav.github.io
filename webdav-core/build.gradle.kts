plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    // HTTP Client
    api("com.squareup.okhttp3:okhttp:4.12.0")

    // XML parsing (optional - we use regex for simplicity and reliability)
    // implementation("org.xmlpull:xmlpull:1.1.4.4")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
