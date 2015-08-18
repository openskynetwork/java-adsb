package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensky.example.ModeSEncodedMessage;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.PositionDecoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.SurfacePositionMsg;
import org.opensky.libadsb.msgs.VelocityOverGroundMsg;

/**
 * OpenSky AVRO to CSV converter
 * Note: We assume, that messages are more or less ordered by time
 * 
 * Generates CSV with flights from avro file.
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 *
 */
public class Avro2CSV {
	
	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"Avro2CSV [options/filters] avro-file",
				"\nOpenSky AVRO to CSV converter\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}
	
	/**
	 * This class is a container for all information
	 * about flights that are relevant for the CSV
	 * generation
	 */
	private class Flight {
		public String icao24;
		public char[] callsign;
		public long id; // flight id
		public Double last; // last position message received
		public Double lastVelocityUpdate; // last position message received
		public Double lastVelocity; // last position message received
		public Double lastHeading; // last position message received
		public Double lastVerticalRate; // last position message received
		public Double lastPositionUpdate; // last position message received
		public Position lastPosition; // ordered list of coordinates
		public PositionDecoder dec; // stateful position decoder
		
		public Flight () {
			dec = new PositionDecoder();
			lastPosition = new Position();
			callsign = null;
		}
	}
	
	public static void printCSVLine(Flight flight, boolean velocity) {
		// id,icao24,callsign,lastLatitude,lastLongitude,lastAltitude,lastPositionUpdate,lastVelocity,lastHeading,lastVerticalRate,lastVelocityUpdate
		String lastLatitude = flight.lastPosition.getLatitude() == null ? "NA" : flight.lastPosition.getLatitude().toString();
		String lastLongitude = flight.lastPosition.getLongitude() == null ? "NA" : flight.lastPosition.getLongitude().toString();
		String lastAltitude = flight.lastPosition.getAltitude() == null ? "NA" : flight.lastPosition.getAltitude().toString();
		System.out.format("%d,\"%s\",%s,%s,%s,%s,%s\n",
				flight.id, flight.icao24,
				flight.callsign != null ? "\""+new String(flight.callsign)+"\"" : "NA",
				lastLatitude, lastLongitude, lastAltitude,
				flight.lastPositionUpdate!=null ? flight.lastPositionUpdate : "NA");
		if (velocity)
			System.out.format(",%s,%s,%s,%s",
					flight.lastVelocity!=null ? flight.lastVelocity : "NA",
					flight.lastHeading!=null ? flight.lastHeading : "NA",
					flight.lastVerticalRate!=null ? flight.lastVerticalRate : "NA",
					flight.lastVelocityUpdate!=null ? flight.lastVelocityUpdate : "NA");
		System.out.println();
	}

	public static void main(String[] args) {
		
		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("i", "icao24", true, "filter by icao 24-bit address (hex)");
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");
		opts.addOption("n", "max-num", true, "max number of flights written to CSV");
		opts.addOption("novelocity", false, "disable CSV entries for velocity updates");
		opts.addOption("noposition", false, "disable CSV entries for position updates");
		opts.addOption("noident", false, "disable CSV entries for identification updates");
		
		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		File avro = null;
		String filter_icao24 = null;
		Long filter_max = null;
		Double filter_start = null, filter_end = null;
		String file = null;
		boolean novelocity = false, noposition = false, noident = false;
		try {
			cmd = parser.parse(opts, args);
			
			// parse arguments
			try {
				if (cmd.hasOption("i")) filter_icao24 = cmd.getOptionValue("i");
				if (cmd.hasOption("s")) filter_start = Double.parseDouble(cmd.getOptionValue("s"));
				if (cmd.hasOption("e")) filter_end = Double.parseDouble(cmd.getOptionValue("e"));
				if (cmd.hasOption("n")) filter_max = Long.parseLong(cmd.getOptionValue("n"));
				novelocity = cmd.hasOption("novelocity");
				noposition = cmd.hasOption("noposition");
				noident = cmd.hasOption("noident");
			} catch (NumberFormatException e) {
				throw new ParseException("Invalid arguments: "+e.getMessage());
			}
			
			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}
			
			// get filename
			if (cmd.getArgList().size() != 1)
				throw new ParseException("No avro file given or invalid arguments.");
			file = cmd.getArgList().get(0);
			
		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}
		
		// check if file exists
		try {
			avro = new File(file);
			if(!avro.exists() || avro.isDirectory() || !avro.canRead()) {
				throw new FileNotFoundException("Avro file not found or cannot be read.");
			}
		} catch (FileNotFoundException e) {
			// avro file not found
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}
		
		// print header
		System.out.print("id,icao24,callsign,lastLatitude,lastLongitude,lastAltitude,lastPositionUpdate");
		if (!novelocity)
			System.out.print("lastVelocity,lastHeading,lastVerticalRate,lastVelocityUpdate");
		System.out.println();
		
		DatumReader<ModeSEncodedMessage> datumReader = new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		long msgCount = 0, good_pos_cnt = 0, bad_pos_cnt = 0, flights_cnt = 0, err_pos_cnt = 0;
		Avro2CSV a2csv = new Avro2CSV();
		try {
			DataFileReader<ModeSEncodedMessage> fileReader = new DataFileReader<ModeSEncodedMessage>(avro, datumReader);
			
			// stuff for handling flights
			ModeSEncodedMessage record = new ModeSEncodedMessage();
			HashMap<String, Flight> flights = new HashMap<String, Flight>();
			Flight flight;
			String icao24;
			
			// message registers
			ModeSReply msg;
			AirbornePositionMsg airpos;
			SurfacePositionMsg surfacepos;
			IdentificationMsg ident;
			VelocityOverGroundMsg velo;
			
			while (fileReader.hasNext()) {
				// get next record from file
				record = fileReader.next(record);
				
				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start)
					continue;
				
				if (filter_end != null && record.getTimeAtServer()>filter_end)
					continue;
				
				// cleanup decoders to avoid excessive memory usage
				// therefore, remove decoders which have not been used for more than one hour.
				List<String> to_remove = new ArrayList<String>();
				for (String key : flights.keySet()) {
					if (flights.get(key).last<record.getTimeAtServer()-3600) {
						to_remove.add(key);
					}
				}
				
				for (String key : to_remove)
					flights.remove(key);

				msgCount++;
				
				try {
					msg = Decoder.genericDecoder(record.getRawMessage().toString());
				} catch (BadFormatException e) {
					continue;
				}
				icao24 = tools.toHexString(msg.getIcao24());
				
				// icao24 filter
				if (filter_icao24 != null && !icao24.equals(filter_icao24))
					continue;
				
				// select current flight
				if (flights.containsKey(icao24))
					flight = flights.get(icao24);
				else {
					// filter max flights
					if (filter_max != null && flights_cnt>filter_max)
						continue;
					flight = a2csv.new Flight();
					flight.icao24 = icao24;
					flight.id = flights_cnt;
					flights.put(icao24, flight);
					++flights_cnt;
				}

				flight.last = record.getTimeAtServer();

				///////// Airborne Position Messages
				if (msg.getType() == ModeSReply.subtype.ADSB_AIRBORN_POSITION) {
					airpos = (AirbornePositionMsg) msg;
					Position rec = record.getSensorLatitude() != null ?
							new Position(
									record.getSensorLongitude(),
									record.getSensorLatitude(),
									record.getSensorAltitude()) : null;
					
					airpos.setNICSupplementA(flight.dec.getNICSupplementA());
					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, airpos);
					if (pos == null)
						++err_pos_cnt;
					else {
						if (pos.isReasonable() && !pos.equals(flight.lastPosition)) {
							flight.lastPosition = pos;
							flight.lastPositionUpdate = record.getTimeAtServer();
							++good_pos_cnt;
							if (!noposition)
								printCSVLine(flight, !novelocity);
						}
						else if (!pos.isReasonable())
							++bad_pos_cnt;
					}
				}
				///////// Surface Position Messages
				else if (msg.getType() == ModeSReply.subtype.ADSB_SURFACE_POSITION) {
					surfacepos = (SurfacePositionMsg) msg;
					Position rec = record.getSensorLatitude() != null ?
							new Position(
									record.getSensorLongitude(),
									record.getSensorLatitude(),
									record.getSensorAltitude()) : null;
					
					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, surfacepos);
					if (pos == null)
						++err_pos_cnt;
					else {
						if (pos.isReasonable() && !pos.equals(flight.lastPosition)) {
							flight.lastPosition = pos;
							flight.lastPositionUpdate = record.getTimeAtServer();
							++good_pos_cnt;
							if (!noposition)
								printCSVLine(flight, !novelocity);
						}
						else if (!pos.isReasonable())
							++bad_pos_cnt;
					}
				}
				///////// Identification Messages
				else if (msg.getType() == ModeSReply.subtype.ADSB_IDENTIFICATION) {
					ident = (IdentificationMsg) msg;
					if (flight.callsign == null || !tools.areEqual(ident.getIdentity(), flight.callsign)) {
						flight.callsign = ident.getIdentity();
						if (!noident)
							printCSVLine(flight, !novelocity);
					}
				}
				///////// Identification Messages
				else if (msg.getType() == ModeSReply.subtype.ADSB_VELOCITY) {
					velo = (VelocityOverGroundMsg) msg;
					if (velo.hasVelocityInfo() && new Double(velo.getVelocity()) != flight.lastVelocity) {
						flight.lastVelocity = velo.getVelocity();
						flight.lastHeading = velo.getHeading();
						if (velo.hasVerticalRateInfo())
							flight.lastVerticalRate = velo.getVerticalRate();
						flight.lastVelocityUpdate = record.getTimeAtServer();
						if (!novelocity)
							printCSVLine(flight, !novelocity);
					}
				}
			}
			
			fileReader.close();
			
		} catch (IOException e) {
			// error while trying to read file
			System.err.println("IO Error: "+e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			// something went wrong
			System.err.println("Something went wrong: "+e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		
		System.err.println("Read "+msgCount+" messages.");
		System.err.println("Good positions: "+good_pos_cnt);
		System.err.println("Bad positions: "+bad_pos_cnt);
		System.err.println("Erroneous positions: "+err_pos_cnt);
		System.err.println("Flights: "+flights_cnt);
	}
}
