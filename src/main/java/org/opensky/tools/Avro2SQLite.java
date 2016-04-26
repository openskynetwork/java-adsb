package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

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
 * OpenSky AVRO to SQLite converter
 * Note: We assume, that messages are more or less ordered by time
 * 
 * Generates SQlite DB with flights from avro file.
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class Avro2SQLite {
	Connection conn = null;
	Statement stmt = null;
	
	// initialize SQLite database
	public Avro2SQLite (String path, boolean nopositions, boolean novelocity) {
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:"+path);
			conn.setAutoCommit(false);
			stmt = conn.createStatement();
			
			// create tables
			String sql = "CREATE TABLE flights\n"+
			             "(id INT PRIMARY KEY,\n"+
					     " first REAL NOT NULL, -- unix timestamp first message\n"+
			             " last REAL NOT NULL, -- unix timestamp last message\n"+
					     " icao24 TEXT NOT NULL, -- icao 24-bit address\n"+
			             " callsign TEXT -- callsign\n"+
					     ")";
			
			stmt.executeUpdate(sql);
			
			if (!nopositions) {
				sql = "CREATE TABLE positions\n"+
						"(flight INT NOT NULL, -- references flight from flights table\n"+
						" timestamp REAL, -- unix timestamp\n"+
						" longitude REAL, -- in decimal degrees\n"+
						" latitude REAL, -- in decimal degrees\n"+
						" altitude REAL, -- in meters\n"+
						" accuracy REAL, -- containment radius in meters\n"+
						" FOREIGN KEY(flight) REFERENCES flights(id)\n"+
						")";
				
				stmt.executeUpdate(sql);
			}
			
			if (!novelocity) {
				sql = "CREATE TABLE velocities\n"+
						"(flight INT NOT NULL, -- references flight from flights table\n"+
						" timestamp REAL, -- unix timestamp\n"+
						" velocity REAL, -- in meters per second\n"+
						" heading REAL, -- in degrees clockwise from geographic north\n"+
						" verticalRate REAL, -- in meters per second\n"+
						" FOREIGN KEY(flight) REFERENCES flights(id)\n"+
						")";
				
				stmt.executeUpdate(sql);
			}
		} catch ( Exception e ) {
			System.err.println("Could not open database: " + e.getMessage() );
			System.exit(1);
		}
	}
	
	public void insertFlight (Flight flight, double time, String icao24) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT INTO flights VALUES (%d, %f, %f, '%s', NULL)",
					flight.id, time, time, icao24);
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not create flight: "+e.getMessage());
			System.exit(1);
		}
	}
	
	public void updateFlight (long flight, Double last, String callsign) {
		try {
			String sql;
			if (last != null) {
				sql = String.format(Locale.ENGLISH, "UPDATE flights SET last=%f WHERE id = %d", last, flight);
				stmt.execute(sql);
			}
			if (callsign != null) {
				sql = String.format(Locale.ENGLISH, "UPDATE flights SET callsign='%s' WHERE id = %d", callsign, flight);
				stmt.execute(sql);
			}
		} catch (Exception e) {
			System.err.println("Could not update flight: "+e.getMessage());
			System.exit(1);
		}
	}
	
	public void insertPosition (long flight, double time, Position position, Double radius) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT INTO positions VALUES (%d, %f, %s, %s, %s, %s)",
					flight, time,
					position != null && position.getLongitude() != null ? position.getLongitude().toString() : "NULL",
					position != null && position.getLatitude() != null ? position.getLatitude().toString() : "NULL",
					position != null && position.getAltitude() != null ? position.getAltitude().toString() : "NULL",
					radius != null && radius != -1.0 ? radius.toString() : "NULL");
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not insert position: "+e.getMessage());
			System.exit(1);
		}
	}
	
	public void insertVelocity (long flight, double time, Double velocity, Double heading, Double vertical_rate) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT INTO velocities VALUES (%d, %f, %s, %s, %s)",
					flight, time,
					velocity != null ? velocity.toString() : "NULL",
					heading != null ? heading.toString() : "NULL",
					vertical_rate != null ? vertical_rate.toString() : "NULL");
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not insert velocity: "+e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"Avro2SQLite [options/filters] avro-file sqlite-file",
				"\nOpenSky AVRO to SQLite converter\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	/**
	 * This class is a container for all information
	 * about flights that are relevant for the SQLite DB
	 * generation
	 */
	private class Flight {
		public long id; // flight id
		public Double last; // last position message received
		public PositionDecoder dec; // stateful position decoder
		public Position last_position;
		public double last_velocity;
		public double last_heading;
		public double last_vertical_rate;

		public Flight (long id) {
			dec = new PositionDecoder();
			this.id = id;
			this.last_position = new Position();
		}
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("i", "icao24", true, "filter by icao 24-bit address (hex)");
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");
		opts.addOption("n", "max-num", true, "max number of flights written to the SQLite DB");
		opts.addOption("novelocity", false, "disable DB entries for velocity updates");
		opts.addOption("noposition", false, "disable DB entries for position updates");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		String filter_icao24 = null;
		Long filter_max = null;
		Double filter_start = null, filter_end = null;
		String inpath = null, outpath = null;
		boolean novelocity = false, noposition = false;
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
			} catch (NumberFormatException e) {
				throw new ParseException("Invalid arguments: "+e.getMessage());
			}

			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}

			// get filename
			if (cmd.getArgList().size() != 2)
				throw new ParseException("Output SQLite file is missing!");
			inpath = cmd.getArgList().get(0);
			outpath = cmd.getArgList().get(1);

		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}

		// check if file exists
		File avro = null;
		try {
			// check if output DB exists
			avro = new File(outpath);
			if (avro.exists() && !avro.isDirectory())
				throw new IOException("Output database already exists.");

			// check input file
			avro = new File(inpath);
			if(!avro.exists() || avro.isDirectory() || !avro.canRead())
				throw new FileNotFoundException("Avro file not found or cannot be read.");
		} catch (IOException e) {
			// avro file not found
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}

		// AVRO file reader
		DatumReader<ModeSEncodedMessage> datumReader =
				new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);

		// some counters for statistics
		long msgs_cnt = 0, good_pos_cnt = 0, bad_pos_cnt = 0,
				flights_cnt = 0, filtered_cnt = 0, ignored_cnt = 0,
				last_msgs_cnt = 0;
		long last_time;

		// just a temporary instance for creating Flight-objects
		Avro2SQLite a2sql = new Avro2SQLite(outpath, noposition, novelocity);
		try {
			// open input file
			DataFileReader<ModeSEncodedMessage> fileReader =
					new DataFileReader<ModeSEncodedMessage>(avro, datumReader);

			// stuff for handling flights
			ModeSEncodedMessage record = new ModeSEncodedMessage();
			HashMap<String, Flight> flights = new HashMap<String, Flight>();

			// temporary pointers
			Flight flight;
			String icao24;

			// message registers
			ModeSReply msg;
			AirbornePositionMsg airpos;
			SurfacePositionMsg surfacepos;
			IdentificationMsg ident;
			VelocityOverGroundMsg velo;

			// for flight handling
			List<String> flights_to_remove = new ArrayList<String>();

			// for msg rate
			last_time = System.currentTimeMillis();
			while (fileReader.hasNext()) {
				// count messages
				msgs_cnt++;
				
				// print processing rate
				if (System.currentTimeMillis() - last_time > 1000) {
					System.err.format("\r%6d msgs/s", msgs_cnt-last_msgs_cnt);
					last_time = System.currentTimeMillis();
					last_msgs_cnt = msgs_cnt;
				}

				// get next record from file
				record = fileReader.next(record);

				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start) {
					filtered_cnt++;
					continue;
				}
				if (filter_end != null && record.getTimeAtServer()>filter_end) {
					filtered_cnt++;
					continue;
				}

				// cleanup decoders every 1000000 messages to avoid excessive memory usage
				// therefore, remove decoders which have not been used for more than one hour.
				if (msgs_cnt%1000000 == 0) {
					for (String key : flights.keySet()) {
						if (flights.get(key).last<record.getTimeAtServer()-3600) {
							flights_to_remove.add(key);
						}
					}

					// remove and clear
					for (String key : flights_to_remove)
						flights.remove(key);
					flights_to_remove.clear();
				}

				try {
					msg = Decoder.genericDecoder(record.getRawMessage().toString());
				} catch (BadFormatException e) {
					continue;
				}
				icao24 = tools.toHexString(msg.getIcao24());

				// icao24 filter
				if (filter_icao24 != null && !icao24.equals(filter_icao24)) {
					filtered_cnt++;
					continue;
				}

				// select current flight
				if (flights.containsKey(icao24))
					flight = flights.get(icao24);
				else {
					// filter max flights
					if (filter_max != null && flights_cnt>filter_max) {
						filtered_cnt++;
						continue;
					}

					// new flight
					flight = a2sql.new Flight(flights_cnt);
					flights.put(icao24, flight);
					++flights_cnt;

					a2sql.insertFlight(flight, record.getTimeAtServer(), icao24);
					
				}

				flight.last = record.getTimeAtServer();
				a2sql.updateFlight(flight.id, flight.last, null);

				///////// Airborne Position Messages
				if (!noposition && msg.getType() == ModeSReply.subtype.ADSB_AIRBORN_POSITION) {
					airpos = (AirbornePositionMsg) msg;
					Position rec = record.getSensorLatitude() != null ?
							new Position(
									record.getSensorLongitude(),
									record.getSensorLatitude(),
									record.getSensorAltitude()) : null;

					airpos.setNICSupplementA(flight.dec.getNICSupplementA());
					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, airpos);
					if (pos == null || !pos.isReasonable())
						++bad_pos_cnt;
					else if (pos.isReasonable() && !pos.equals(flight.last_position)) { // filter duplicate positions
						flight.last_position = pos;
						++good_pos_cnt;
						a2sql.insertPosition(flight.id, flight.last, pos, airpos.getHorizontalContainmentRadiusLimit());
					}
				}

				///////// Surface Position Messages
				else if (!noposition && msg.getType() == ModeSReply.subtype.ADSB_SURFACE_POSITION) {
					surfacepos = (SurfacePositionMsg) msg;
					Position rec = record.getSensorLatitude() != null ?
							new Position(
									record.getSensorLongitude(),
									record.getSensorLatitude(),
									record.getSensorAltitude()) : null;

					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, surfacepos);
					if (pos == null || !pos.isReasonable())
						++bad_pos_cnt;
					else if (pos.isReasonable() && !pos.equals(flight.last_position)) { // filter duplicate positions
						flight.last_position = pos;
						++good_pos_cnt;
						a2sql.insertPosition(flight.id, flight.last, pos, surfacepos.getHorizontalContainmentRadiusLimit());
					}
				}

				///////// Identification Messages
				else if (msg.getType() == ModeSReply.subtype.ADSB_IDENTIFICATION) {
					ident = (IdentificationMsg) msg;
					a2sql.updateFlight(flight.id, null, new String(ident.getIdentity()));
				}

				///////// Velocity Messages
				else if (!novelocity && msg.getType() == ModeSReply.subtype.ADSB_VELOCITY) {
					velo = (VelocityOverGroundMsg) msg;
					if (velo.hasVelocityInfo() &&
							velo.getVelocity() != flight.last_velocity &&
							velo.getHeading() != flight.last_heading) { // only updates
						flight.last_velocity = velo.getVelocity();
						flight.last_heading = velo.getHeading();
						if (velo.hasVerticalRateInfo())
							flight.last_vertical_rate = velo.getVerticalRate();

						a2sql.insertVelocity(flight.id, flight.last, flight.last_velocity, flight.last_heading,
								velo.hasVerticalRateInfo() ? velo.getVerticalRate() : null);
					}
					else if (velo.hasVerticalRateInfo() && velo.getVerticalRate() != flight.last_vertical_rate) {
						flight.last_vertical_rate = velo.getVerticalRate();
						a2sql.insertVelocity(flight.id, flight.last, null, null, flight.last_vertical_rate);
					}
				}
				
				// ignore any other message
				else ignored_cnt++;
			}
			
			a2sql.conn.commit();

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
		
		System.err.println("\n\nStatistics:");
		System.err.format("\tTotal messages: %d\n", msgs_cnt);
		System.err.format("\tFiltered messages: %d\n", filtered_cnt);
		System.err.format("\tIgnored messages: %d\n", ignored_cnt);
		System.err.format("\tFlights: %d\n\n", flights_cnt);
		System.err.format("\tGood positions: %d\n", good_pos_cnt);
		System.err.format("\tBad positions: %d\n", bad_pos_cnt);
	}
}
