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

## Usage

A complete working decoder can be found in [ExampleDecoder.java](src/main/java/org/opensky/example/ExampleDecoder.java). A demonstration how this
decoder can be used is provided in [OskySampleReader.java](https://github.com/openskynetwork/osky-sample/blob/master/src/main/java/org/opensky/tools/OskySampleReader.java). It reads, decodes, and prints serialized
ADS-B messages from avro-files with the OpenSky schema. A sample of such data and the schema is provided in the
[osky-sample repository](https://github.com/openskynetwork/osky-sample).


## Migration to Version 3

**!!Version 3 is not a drop-in replacement. You have to adapt your existing code!!**

With libadsb version 3, many things have changed, including:
* **Switched to nautical units for altitude and speed.** We keep the values according to the standard.
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
* `getTransponderAddress()` returning integer representation of the ICAO 24 bit
  transponder address in addition to `getIcao24()` which returns raw bytes

### Migration Guide

#### General

* Remove MissingInformationExceptions, missing values are given as null instead
* Checking if info available is still possible:
    * Velocity:
      * `hasVerticalRateInfo()`
      * `hasVelocityInfo()`
      * `hasGeoMinusBaroInfo()`
* Altitudes are Integers (not doubles)
* Altitude in feet, not meters (multiply with 0.3048 to keep metric system);
  The following message types are affected:
  * `AltitudeReply`
  * `CommBAltitudeReply`
  * `ShortACAS`
  * `LongACAS`
  * `AirbornePositionV?Msg`

#### Position Messages

* Replace ModeSReply.subtype.ADSB_AIRBORN_POSITION with
  * ModeSReply.subtype.ADSB_AIRBORN_POSITION_V0
  * ModeSReply.subtype.ADSB_AIRBORN_POSITION_V1
  * ModeSReply.subtype.ADSB_AIRBORN_POSITION_V2

* Replace ModeSReply.subtype.ADSB_SURFACE_POSITION with
  * ModeSReply.subtype.ADSB_SURFACE_POSITION_V0
  * ModeSReply.subtype.ADSB_SURFACE_POSITION_V1
  * ModeSReply.subtype.ADSB_SURFACE_POSITION_V2

* No need to distinguish position messages of different versions for Decoder.
  Their common super class is `SurfacePositionV0Msg`/`AirbornePositionV0Msg` and
  ADS-B version is irrelevant for position decoding

* No `isBarometricAltitude` for surface position messages anymore

* NIC supplements only available in versions 1 and 2 (new method hasNICSupplementX)
* Method for time flag renamed to `hasTimeFlag`
* Ground speed in surface positions in knots instead of m/s (multiply with 0.514444 to keep metric system)

#### Operational Status Messages

* Operational Status now in four different classes. No need to distinguish
  subtype codes by the user (airborne = 0, surface = 1)
  * Have a look at the API to see available fields of different ADS-B versions
  * V0: only TCAS and CDTI, no distinction between airborne and surface
  * V2: only SIL supplement is new in version 2, if not needed you can use V1
    for both versions

* Replace ModeSReply.subtype.ADSB_STATUS with
  * ModeSReply.subtype.ADSB_STATUS_V0
  * ModeSReply.subtype.ADSB_AIRBORN_STATUS_V1
  * ModeSReply.subtype.ADSB_AIRBORN_STATUS_V2

* Replace ModeSReply.subtype.ADSB_STATUS with
  * ModeSReply.subtype.ADSB_STATUS_V0
  * ModeSReply.subtype.ADSB_SURFACE_STATUS_V1
  * ModeSReply.subtype.ADSB_SURFACE_STATUS_V2

#### Velocity and Airspeed Messages

* VelocityOverground: NACv replaced `getNavigationAccuracyCategory` and returns meters, 
  not the category as byte
* Velocity now in knots instead of m/s (multiply with 0.514444 to keep metric system)
* Vertical rate in feet/min instead of m/s (multiply with 0.00508 to keep metric system)
* geoMinurBaro in feet, not meters (multiply with 0.3048 to keep metric system)


#### Long/Short ACAS

* MaximumAirspeed in knots instead of m/s (multiply with 0.514444 to keep metric system)
