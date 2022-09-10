Note: a more elaborate and maintained branch of the library can be found here: https://github.com/SeRoSystems/lib1090

java-adsb
=========

This is a Mode S and ADS-B decoding library for Java. It is a product of the OpenSky Network project (http://www.opensky-network.org). It is based on these two references:
* ICAO Aeronautical Telecommunications Annex 10 Volume 4 (Surveillance Radar and Collision Avoidance Systems)
* RTCA DO-260B "Minimum Operational Performance Standards (MOPS) for 1090ES"

It supports the following Mode S downlink formats:
* DF 0: Short air-air ACAS
* DF 4: Short altitude reply
* DF 5: Short identify reply
* DF 11: All-call reply
* DF 16: Long air-air ACAS
* DF 17/18: Extended Squitter (see ADS-B formats below)
* DF 19: Military Extended Squitter
* DF 20: Comm-B altitude reply
* DF 21: Comm-B identify reply
* DF >24: Comm-D Extended Length Message

The following ADS-B formats are supported:
* BDS 0,5: Airborne position messages (including global and local CPR)
* BDS 0,6: Surface position messages (including global and local CPR)
* BDS 0,8: Identification messages
* BDS 0,9: Airborne velocity messages
* BDS 6,1: Aircraft status reports (emergency/priority, TCAS RA)
* BDS 6,2: Target state and status messages
* BDS 6,5: Operational status reports (airborne and surface)

The Comm-B registers, Comm-D data link and military ES are not parsed. Comm-B and D will follow at some point.

The formats are implemented according to RTCA DO-260B, i.e. ADS-B Version 2. The decoder properly takes care of older versions.

### Packaging

This is a Maven project. You can simply generate a jar file with `mvn package`.
All the output can afterwards be found in the `target` directory. There will
be two jar files

* `libadsb-VERSION.jar` contains libadsb, only.
* `libadsb-VERSION-fat.jar` includes libadsb and all its dependencies.

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

There are three different versions of ADS-B in the wild: 0, 1 and 2.
Transmitters' ADS-B version affects interpretation of certain messages. This holds in particular for aircraft's 
operational status information such as position accuracy and system equipage.

When decoding ADS-B, a receiver by default assumes version 0, unless told otherwise. This has no effect on basic 
information such as position and velocity. These fields are compatible between different versions. However, for full
awareness about an aircraft's capabilities, version information has to be taken into account. It is encoded in the
operational status message which is sent around every five seconds. Thus, decoding ADS-B has to be done in a stateful 
manner. Libadsb includes a stateful decoder which can be found in the class `ModeSDecoder`. Given a stream of encoded
messages, it returns decoded results of the inferred ADS-B version. This is represented by different types, e.g.,
`AirbornePositionV0Msg` and `AirbornePositionV1Msg` for versions 0 and 1. 

We recommend having a look at the [ExampleDecoder.java](src/main/java/org/opensky/example/ExampleDecoder.java) which
gives a detailed explanation on how to use libadsb.

A demonstration how this decoder can be used is provided in [OskySampleReader.java](https://github.com/openskynetwork/osky-sample/blob/master/src/main/java/org/opensky/tools/OskySampleReader.java). It reads, decodes, and prints serialized
ADS-B messages from avro-files with the OpenSky schema. A sample of such data and the schema is provided in the
[osky-sample repository](https://github.com/openskynetwork/osky-sample).


## Migration to Version 3

**!!If you have been using libadsb version 2.x and earlier: Version 3 is not a drop-in replacement. You have to adapt your existing code!!**

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
* Added `distance3d()` method to `Position` and renamed `distanceTo()` to `haversine()`


### Migration Guide

#### General

* Remove MissingInformationExceptions, missing values are given as null instead
* Checking if info available is still possible:
    * Velocity:
      * `hasVerticalRateInfo()`
      * `hasVelocityInfo()`
      * `hasGeoMinusBaroInfo()`
* Altitudes are Integers (not doubles)
* Altitude in feet, not meters (use `tools.feet2Meters()` for conversion);
  The following message types are affected:
  * `AltitudeReply`
  * `CommBAltitudeReply`
  * `ShortACAS`
  * `LongACAS`
  * `AirbornePositionV?Msg`
* Replace all occurences of `Position.distanceTo()` with `Position.haversine()`

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
  the ADS-B version is irrelevant for *position* decoding

* No `isBarometricAltitude` for surface position messages anymore

* NIC supplements only available in versions 1 and 2 (new method hasNICSupplementX)
* Method for time flag renamed to `hasTimeFlag`
* Ground speed in surface positions in knots instead of m/s (use `tools.knots2MetersPerSecond()` for conversion)

#### Operational Status Messages

* Operational Status now in four different classes. No need to distinguish
  subtype codes by the user (airborne = 0, surface = 1)
  * Have a look at the API to see available fields of different ADS-B versions. Many things have changed here.
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

* VelocityOverground: `getNavigationAccuracyCategory` replaced by `getNACv` and returns meters,
  not the category as byte
* Velocity now in knots instead of m/s (use `tools.knots2MetersPerSecond()` for conversion)
* Vertical rate in feet/min instead of m/s (use `tools.feetPerMinute2MetersPerSecond()` for conversion)
* geoMinurBaro in feet, not meters (use `tools.feet2Meters()` for conversion)


#### Long/Short ACAS

* MaximumAirspeed in knots instead of m/s (use `tools.knots2MetersPerSecond()` for conversion)

