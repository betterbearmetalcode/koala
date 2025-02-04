plugins {
    id("java")
    id("maven-publish")
}

group = "org.tahomarobotics.scouting"
version = "1.0-rc1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.jmdns:jmdns:3.6.0")
    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            description = "Koala is a highly customized data transfer, database manager, and TBA data fetcher rolled into one. It is a library designed for the Bear Metal Scouting suite of applications."
            url = uri("https://maven.pkg.github.com/betterbearmetalcode/koala")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}