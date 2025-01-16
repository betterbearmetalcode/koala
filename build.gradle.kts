plugins {
    id("java")
}

group = "org.tahomarobotics.scouting"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
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