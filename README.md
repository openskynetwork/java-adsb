java-adsb
=========

This is an ADS-B decoding library for Java. It also supports Mode S messages. It is part of the OpenSky Network project (http://www.opensky-network.org).

Currently it only supports the following ADS-B messages:
* Identification messages
* Velocity over ground messages
* Airborne Position messages (including global and local CPR)

These are the most commonly used formats, but more formats will follow soon.

### Example decoding of position message
```java
import org.opensky.libadsb.*;

// ...

// Example messages for position (47.0063,8.0254)
ModeSReply odd = Decoder.genericDecoder("8dc0ffee58b986d0b3bd25000000");
ModeSReply even = Decoder.genericDecoder("8dc0ffee58b9835693c897000000");

// test for message type with "instanceof"

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

An complete example can be found in ExampleDecoder.java
