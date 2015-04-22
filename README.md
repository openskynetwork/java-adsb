java-adsb
=========

This is an ADS-B decoding library for Java. It also supports Mode S messages. It is part of the OpenSky Network project (http://www.opensky-network.org).

Currently it only supports the following ADS-B messages:
* Identification messages
* Velocity over ground messages
* Airborne Position messages (including global and local CPR)
* Surface Position messages (including global and local CPR)
* Operational status reports (airborne and surface)
* Aircraft status reports (emergency/priority, TCAS RA)

The formats are implemented according to RTCA DO-260B, i.e. ADS-B Version 2. Most message formats of ADS-B Version 1 are upward compatible.
Please check the API documentation of the message formats for differences. The ADS-B version of transponders can be obtained in Aircraft
Operational Status reports (type code 31; `OperationalStatusMsg.getVersion()`).

### Packaging

This is a Maven project. You can simply generate a jar file with `mvn package`.
All the output can afterwards be found in the `target` directory. There will
be two jar files

* `libadsb-1.0.jar` contains libadsb, only. You should use this in your projects
* `libadsb-1.0-fat.jar` is packaged with all dependencies to read AVRO files. You should use it to decode OpenSky Avro dumps.

### Example decoding of position message
```java
import org.opensky.libadsb.*;

// ...

// Example messages for position (47.0063,8.0254)
ModeSReply odd = Decoder.genericDecoder("8dc0ffee58b986d0b3bd25000000");
ModeSReply even = Decoder.genericDecoder("8dc0ffee58b9835693c897000000");

// test for message type with "instanceof"
if (!(odd instanceof AirbornePositionMsg) ||
    !(even instanceof AirbornePositionMsg)) {
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

A complete example can be found in ExampleDecoder.java. It reads messages line-by-line from STDIN and prints the decoded information. You can use it as follows after packaging:
`tail src/test/resources/messages.txt | java -cp target/libadsb-1.0.jar org.opensky.example.ExampleDecoder`
or pipe messages from your receiver to it.

### Reading OpenSky sample data

You can find some OpenSky sample data [here](https://github.com/openskynetwork/osky-sample).
Here's how to show the first `n` messages of an Avro dump:
`java -jar target/libadsb-fat.jar /path/to/osky-sample/avro/raw20150421 n`
