package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
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
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.SurfacePositionMsg;

/**
 * This file filters data in avro files by geographic area and stores
 * the result in an avro file again.
 * Note: We assume, that messages are ordered by time in avro file
 * 
 * Generates KML file with flights from avro file.
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 *
 */
public class ExtractArea {

	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"ExtractArea [options/filters] -c lon,lat -r radius in.avro out.avro",
				"\nFilter messages from OpenSky's AVRO files for an area of interest\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	/**
	 * This class is a container for all information
	 * about flights that are relevant for the KML
	 * generation
	 */
	private class Flight {
		public PositionDecoder dec; // stateful position decoder
		boolean is_in_area;
		double last;


		public Flight () {
			dec = new PositionDecoder();
			is_in_area = false;
		}
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("i", "icao24", true, "filter by icao 24-bit address (hex)");
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");
		opts.addOption("n", "max-num", true, "max number of flights written to KML");
		opts.addOption("c", "center", true, "center of the area in decimal degrees");
		opts.addOption("r", "radius", true, "radius of the area in meters");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		File infile = null, outfile = null;
		String filter_icao24 = null;
		Long filter_max = null;
		Double filter_start = null, filter_end = null, radius = null;
		String cntr = null, in = null, out = null;
		Position center = null;
		try {
			cmd = parser.parse(opts, args);

			// parse arguments
			try {
				if (cmd.hasOption("i")) filter_icao24 = cmd.getOptionValue("i");
				if (cmd.hasOption("s")) filter_start = Double.parseDouble(cmd.getOptionValue("s"));
				if (cmd.hasOption("e")) filter_end = Double.parseDouble(cmd.getOptionValue("e"));
				if (cmd.hasOption("n")) filter_max = Long.parseLong(cmd.getOptionValue("n"));
				if (cmd.hasOption("c")) cntr = cmd.getOptionValue("c");
				else throw new ParseException("Center of area of interest is missing.");
				center = new Position(
						Double.parseDouble(cntr.split(",")[0]),
						Double.parseDouble(cntr.split(",")[1]),
						0.0);
				if (cmd.hasOption("r")) radius = Double.parseDouble(cmd.getOptionValue("r"));
				else throw new ParseException("Radius of area of interest is missing.");
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
				throw new ParseException("Invalid arguments: need both, input and output file.");
			in = cmd.getArgList().get(0);
			out = cmd.getArgList().get(1);

		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}
		
		System.out.format("Center: lon %f, lat %f\n", center.getLongitude(), center.getLatitude());
		System.out.format("Radius: %f meters\n", radius);

		System.out.println("Opening avro file.");

		// check if file exists
		try {
			infile = new File(in);
			if(!infile.exists() || infile.isDirectory() || !infile.canRead()) {
				throw new FileNotFoundException("Input avro file not found or cannot be read.");
			}

			outfile = new File(out);
			if(outfile.exists())
				throw new java.io.IOException("Output avro file already exists.");
		} catch (FileNotFoundException e) {
			// avro file not found
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		} catch (IOException e) {
			// cannot write to KML
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}

		DatumReader<ModeSEncodedMessage> datumReader = new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		DatumWriter<ModeSEncodedMessage> datumWriter = new SpecificDatumWriter<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		long inCount = 0, outCount = 0, flights_cnt = 0;
		try {
			DataFileReader<ModeSEncodedMessage> fileReader = new DataFileReader<ModeSEncodedMessage>(infile, datumReader);
			DataFileWriter<ModeSEncodedMessage> fileWriter = new DataFileWriter<ModeSEncodedMessage>(datumWriter);
			fileWriter.create(ModeSEncodedMessage.getClassSchema(), outfile);

			// stuff for handling flights
			ModeSEncodedMessage record = new ModeSEncodedMessage();
			HashMap<String, Flight> flights = new HashMap<String, Flight>();
			Flight flight;
			String icao24;

			// message registers
			ModeSReply msg;
			AirbornePositionMsg airpos;
			SurfacePositionMsg surfacepos;

			// for handling flights
			ExtractArea aoi = new ExtractArea();

			while (fileReader.hasNext()) {
				// get next record from file
				record = fileReader.next(record);

				inCount++;

				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start)
					continue;

				if (filter_end != null && record.getTimeAtServer()>filter_end)
					continue;

				// cleanup decoders every 1.000.000 messages to avoid excessive memory usage
				// therefore, remove decoders which have not been used for more than one hour.
				if (inCount%1000000 == 0) {
					List<String> to_remove = new ArrayList<String>();
					for (String key : flights.keySet()) {
						if (flights.get(key).last<record.getTimeAtServer()-3600) {
							to_remove.add(key);
						}
					}

					for (String key : to_remove)
						flights.remove(key);
				}

				try {
					msg = Decoder.genericDecoder(record.getRawMessage().toString());
				} catch (BadFormatException e) {
					continue; // also filter bad messages
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
					flight = aoi.new Flight();
					flights.put(icao24, flight);
					flights_cnt++;
				}
				
				flight.last = record.getTimeAtServer();

				///////// Airborne Position Messages
				if (msg.getType() == ModeSReply.subtype.ADSB_AIRBORN_POSITION) {
					airpos = (AirbornePositionMsg) msg;
					Position rec = record.getSensorLatitude() != null ?
							new Position(record.getSensorLongitude(), record.getSensorLatitude(), 0.0) : null;
					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, airpos);
					if (pos != null) {
						pos.setAltitude(0.0); // two-dimensional radius
						if (pos.isReasonable() && pos.distanceTo(center)<=radius)
							flight.is_in_area = true;
						else if (pos.isReasonable() && pos.distanceTo(center)>radius)
							flight.is_in_area = false;
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
					if (pos != null) {
						pos.setAltitude(0.0); // two-dimensional radius
						if (pos.isReasonable() && pos.distanceTo(center)<=radius)
							flight.is_in_area = true;
						else if (pos.isReasonable() && pos.distanceTo(center)>radius)
							flight.is_in_area = false;
					}
				}
				
				if (flight.is_in_area) {
					fileWriter.append(record);
					++outCount;
				}
			}

			fileReader.close();
			fileWriter.close();
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

		System.err.println("Read "+inCount+" messages.");
		System.err.println("Wrote "+outCount+" messages.");
		System.err.println("Number of flights was "+flights_cnt);
	}
}
