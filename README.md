![A graphic showing the TBA (The Blue Alliance) Logo, a Laptop, and a Phone connected wirelessly](/docs/assets/readme-graphic.png)
# Koala
[![JitPack](https://jitpack.io/v/betterbearmetalcode/koala.svg)](https://jitpack.io/#betterbearmetalcode/koala)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/w/betterbearmetalcode/koala)](https://github.com/betterbearmetalcode/koala/commits/main/)

Koala is a highly customized data transfer, database manager, and TBA data fetcher rolled into one. It is a library designed for the Bear Metal Scouting suite of applications.
## How it works
### Connections
- The server broadcasts mDNS that the client is constantly looking for. Once found, the client opens a socket connection to the server. 
- Later, the client will choose when to send data to the server. The server will then add the data to the MongoDB database.
### Data
- All data is sent in JSON. The JSON includes a section for the header, which defines what type of scouting data is being sent (either team scout, strat scout, or pits scout). 
- Once the data gets to the server, it is added to the MongoDB database. 
- Additionally, before matches, the server will get all the teams playing in that match and get their details from The Blue Alliance (TBA). This data is then added to the database as well.
- Finally, the Scouting Server Application will visualize the data and create an initial picklist.

# Installation
We use JitPack to distribute Koala. To install Koala, add the following to your build.gradle or pom.xml file and replace `$version` with the version you want to use.
## Gradle
### Groovy DSL (build.gradle)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```
```groovy
dependencies {
    implementation 'com.github.betterbearmetalcode:koala:$version'
}
```

### Kotlin DSL (build.gradle.kts)

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
```
```kotlin
dependencies {
  implementation("com.github.betterbearmetalcode:koala:$version")
}
```

## Maven

```xml
<repositories>
  <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
</repositories>
```
```xml
<dependency>
    <groupId>com.github.betterbearmetalcode</groupId>
    <artifactId>koala</artifactId>
    <version>$version</version>
</dependency>
```

# How to use the built-in TBA API fetcher
TBA requires an API key to access their data. If you don't have one yet, go to [their website](https://www.thebluealliance.com/) and create an account. Once you have an account, go to your account settings and create a new read-only API key.
Next, set the environment variable `KOALA_TBA_API_KEY` to your API key. Done! Now you can use the TBA fetcher.
