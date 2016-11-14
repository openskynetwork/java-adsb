java-adsb
=========

This is a Mode S and ADS-B decoding library for Java. It is a product of the OpenSky Network project (http://www.opensky-network.org).

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
* Velocity over ground messages
* Airborne Position messages (including global and local CPR)
* Surface Position messages (including global and local CPR)
* Operational status reports (airborne and surface)
* Aircraft status reports (emergency/priority, TCAS RA)

The Comm-B registers, Comm-D data link and military ES are not parsed. Comm-B and D will follow soon.

The formats are implemented according to RTCA DO-260B, i.e. ADS-B Version 2. Most message formats of ADS-B Version 1 are upward compatible. Please check the API documentation of the message formats for differences. The ADS-B version of transponders can be obtained in Aircraft Operational Status reports (type code 31; `OperationalStatusMsg.getVersion()`).

Note: format type code 29 (target state and status information) is missing since it's virtually non-existent in the current ADS-B deployment.

### Packaging

This is a Maven project. You can simply generate a jar file with `mvn package`.
All the output can afterwards be found in the `target` directory. There will
be two jar files

* `libadsb-2.0.jar` contains libadsb, only. You should use this in your projects
* `libadsb-2.0-fat.jar` is packaged with all dependencies to read AVRO files. You should use it to decode OpenSky Avro dumps.

#### Maven Central

We have also published this project on Maven Central. Just include the following dependency in your project:

```
<dependency>
  <groupId>org.opensky-network</groupId>
  <artifactId>libadsb</artifactId>
  <version>2.1</version>
</dependency>
```

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
decoder can be used is provided in [OskySampleReader.java](src/main/java/org/opensky/example/OskySampleReader.java). It reads, decodes, and prints serialized
ADS-B messages from avro-files with the OpenSky schema. A sample of such data and the schema is provided in the
[osky-sample repository](https://github.com/openskynetwork/osky-sample).
