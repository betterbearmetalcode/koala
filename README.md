# Koala
Koala is a highly customized data transfer, database manager, and TBA data fetcher rolled into one. It is a library designed for the Bear Metal Scouting suite of applications.
## Core Concepts
### Code
- Library written in Java.
- The library never has to change from year to year, only frontends do.
### Connections
- Server devices can connect to multiple clients.
- The server broadcasts mDNS that the client is constantly looking for. Once found, a handshake occurs and data transfer begins. 
- Whether a device is a server or a client is either hard coded by the program or chosen by the user.
### Data
- Data is sent in JSON through sockets
- Because FRC rules (and therefore scoring) changes each year, the beginning of the packet will display what year, and therefore what game, we are scoring for.
- Along with the year, we will send an identifier for the match on The Blue Alliance

# Installation
### Gradle

```
  repositories {
      mavenCentral()
      maven { url 'https://jitpack.io' }
  }
  dependencies {
      implementation 'com.github.betterbearmetalcode:koala:$version'
  }
```

#### Kotlin Gradle

```
  repositories {
      mavenCentral()
      maven("https://jitpack.io")
  }
  dependencies {
      implementation("com.github.betterbearmetalcode:koala:$version")
  }
```

### Maven

```
<repositories>
  <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
</repositories>
```
```
<dependency>
    <groupId>com.github.betterbearmetalcode</groupId>
    <artifactId>koala</artifactId>
    <version>$version</version>
</dependency>
```
