java-adsb
=========

This is a Mode S and ADS-B decoding library for Java. It is a product of the OpenSky Network project (http://www.opensky-network.org). It is based on these two references:
* ICAO Aeronautical Telecommunications Annex 10 Volume 4 (Surveillance Radar and Collision Avoidance Systems)
* RTCA DO-260 "Minimum Operational Performance Standards (MOPS) for 1090ES"

It supports the following Mode S downlink formats:
* Short air-air ACAS (DF 0)
* Short altitude reply (DF 4)
* Short identify reply (DF 5)
* All-call reply (DF 11)
* Long air-air ACAS (DF 16)
* Extended Squitter (DF 17, 18; see ADS-B formats below)
* Military Extended Squitter (DF 19)
* Comm-B altitude reply (DF 20)
* Comm-B identify reply (DF 21)
* Comm-D Extended Length Message (DF >24)

Currently it supports the following ADS-B formats:
* Identification messages
* Airborne velocity messages
* Airborne position messages (including global and local CPR)
* Surface position messages (including global and local CPR)
* Operational status reports (airborne and surface)
* Aircraft status reports (emergency/priority, TCAS RA)

The Comm-B registers, Comm-D data link and military ES are not parsed. Comm-B and D will follow soon.

The formats are implemented according to RTCA DO-260B, i.e. ADS-B Version 2. The decoder takes care of proper older versions.

Note: format type code 29 (target state and status information) is missing at the moment.

### Packaging

This is a Maven project. You can simply generate a jar file with `mvn package`.
All the output can afterwards be found in the `target` directory. There will
be two jar files

* `libadsb-VERSION.jar` contains libadsb, only. You should use this in your projects

#### Maven Central

We have also published this project on Maven Central. Just include the following dependency in your project:

```
<dependency>
  <groupId>org.opensky-network</groupId>
  <artifactId>libadsb</artifactId>
  <version>VERSION</version>
</dependency>
```

Get the latest version number [here](https://mvnrepository.com/artifact/org.opensky-network/libadsb).

### Example decoding of position message
```java
import org.opensky.libadsb.*;

// ...

// Example messages for position (47.0063,8.0254)
ModeSReply odd = Decoder.genericDecoder("8dc0ffee58b986d0b3bd25000000");
ModeSReply even = Decoder.genericDecoder("8dc0ffee58b9835693c897000000");

// test for message types
if (odd.getType() != ADSB_AIRBORN_POSITION ||
    even.getType() != ADSB_AIRBORN_POSITION) {
    System.out.println("Airborne position reports expected!");
    System.exit(1);
}

// now we know it's an airborne position message
AirbornePositionMsg odd_pos = (AirbornePositionMsg) odd;
AirbornePositionMsg even_pos = (AirbornePositionMsg) even;
double[] lat_lon = odd_pos.getGlobalPosition(even_pos);
double altitude = odd_pos.getAltitude();

System.out.println("Latitude  = "+ lat_lon[0]+ "°\n"+
                   "Longitude = "+ lat_lon[1]+ "°\n"+
                   "Altitude  = "+ altitude+ "m");

// ...
```

A complete working decoder can be found in [ExampleDecoder.java](src/main/java/org/opensky/example/ExampleDecoder.java). A demonstration how this
decoder can be used is provided in [OskySampleReader.java](https://github.com/openskynetwork/osky-sample/blob/master/src/main/java/org/opensky/tools/OskySampleReader.java). It reads, decodes, and prints serialized
ADS-B messages from avro-files with the OpenSky schema. A sample of such data and the schema is provided in the
[osky-sample repository](https://github.com/openskynetwork/osky-sample).


## Migration to Version 3

**!!Version 3 is not a drop-in replacement. You have to adapt your existing code!!**

With libadsb version 3, many things have changed, including:
* Introduction of a stateful decoder to correctly handle transmitters with different ADS-B versions
* Removal `MissingInformationException` - return `null` values instead
* Split operational status message into separate classes for different ADS-B versions
* Split airborne and surface position messages into separate classes for different ADS-B versions
* Proper mapping for NUCr to NACv
* Proper mapping, decoding and description for
  * Horizontal Containment Radius Limit (=Horizontal Protection Limit)
  * Navigation Accuracy Category for position (NACp)
  * Navigation Integrity Category (NIC)
* Added Source Integrity Level to position messages of version 0
