package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import org.apache.commons.lang.StringUtils;
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

import de.micromata.opengis.kml.v_2_2_0.AltitudeMode;
import de.micromata.opengis.kml.v_2_2_0.ColorMode;
import de.micromata.opengis.kml.v_2_2_0.Coordinate;
import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Style;
import de.micromata.opengis.kml.v_2_2_0.TimeSpan;

/**
 * OpenSky AVRO to Google Maps KML converter
 * Note: We assume, that messages are more or less ordered by time
 * 
 * Generates KML file with flights from avro file.
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class Avro2Kml {
	
	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"Avro2Kml [options/filters] avro-file kml-file",
				"\nOpenSky AVRO to Google Maps KML converter\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}
	
	/**
	 * This class is a container for all information
	 * about flights that are relevant for the KML
	 * generation
	 */
	private class Flight {
		public String icao24;
		public char[] callsign;
		public double first; // first message seen
		public double last; // last message seen
		public List<Coordinate> coords; // ordered list of coordinates
		public List<Integer> serials; // flight seen by these sensors
		public PositionDecoder dec; // stateful position decoder
		boolean contains_unreasonable; // true if there was one position with unreasonable flag
		
		public Flight () {
			coords = new ArrayList<Coordinate>();
			dec = new PositionDecoder();
			callsign = new char[0];
			contains_unreasonable = false;
			serials = new ArrayList<Integer>();
		}
	}
	
	/**
	 * Class for generating the kml
	 */
	private class OskyKml {
		private Kml kml;
		private Document doc;
		private Style style;
		private SimpleDateFormat date_formatter;
		private Folder unreasonable;
		private Folder reasonable;
		private Folder empty;
		private int num_flights;
		
		public OskyKml () {
			// prepare KML
			kml = new Kml();
			doc = kml.createAndSetDocument()
					.withName("OpenSky Network");
			
			style = doc.createAndAddStyle()
					.withId("reasonable");
			style.createAndSetLineStyle()
			.withColor("ffffffff")
			.withColorMode(ColorMode.NORMAL)
			.withWidth(1);

			style = doc.createAndAddStyle()
					.withId("unreasonable");
			style.createAndSetLineStyle()
			.withColor("ffd5d5ff")
			.withColorMode(ColorMode.NORMAL)
			.withWidth(1);
			
			unreasonable = doc.createAndAddFolder()
					.withName("Unreasonable Flights");
			reasonable = doc.createAndAddFolder()
					.withName("Reasonable Flights");
			empty = doc.createAndAddFolder()
					.withName("No Positions");
			
			date_formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		}
		
		public void addFlight(Flight flight) {
			Date begin = new Date((long)(flight.first*1000));
			Date end = new Date((long)(flight.last*1000));
			
			Folder which;
			if (flight.coords.size()>0)
				which = flight.contains_unreasonable ? unreasonable : reasonable;
			else which = empty;
			
			String description = "ICAO: "+flight.icao24+"<br />\n"+
							"Callsign: "+new String(flight.callsign)+"<br />\n"+
							"First seen: "+begin.toString()+"<br />\n"+
							"Last seen: "+end.toString()+"<br />\n"+
							"Seen by serials: "+StringUtils.join(flight.serials, ",");
			
			Placemark placemark = which.createAndAddPlacemark()
					.withName(flight.icao24)
					.withTimePrimitive(new TimeSpan()
							.withBegin(date_formatter.format(begin))
							.withEnd(date_formatter.format(end)))
					.withDescription(description)
					.withStyleUrl(flight.contains_unreasonable ? "#unreasonable" : "#reasonable");
			
			placemark.createAndSetLineString()
				.withCoordinates(flight.coords)
				.withAltitudeMode(AltitudeMode.fromValue(AltitudeMode.ABSOLUTE.value()))
				.withId(flight.icao24)
				.withExtrude(false);
			
			num_flights++;
		}
		
		public void writeToFile(File file) {
			try {
				kml.marshal(file);
			} catch (FileNotFoundException e) {
				System.err.println("Could not write to file '"+file.getAbsolutePath()+"'.\nReason: "+e.toString());
			}
		}
		
		public int getNumberOfFlights() {
			return num_flights;
		}
	}

	public static void main(String[] args) {
		
		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("0", "nopos", false, "do not include flight without positions" );
		opts.addOption("i", "icao24", true, "filter by icao 24-bit address (hex)");
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");
		opts.addOption("n", "max-num", true, "max number of flights written to KML");
		
		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		File avro = null, kmlfile = null;
		String filter_icao24 = null;
		Long filter_max = null;
		Double filter_start = null, filter_end = null;
		String file = null, out = null;
		boolean option_nopos = true;
		try {
			cmd = parser.parse(opts, args);
			
			// parse arguments
			try {
				if (cmd.hasOption("i")) filter_icao24 = cmd.getOptionValue("i");
				if (cmd.hasOption("s")) filter_start = Double.parseDouble(cmd.getOptionValue("s"));
				if (cmd.hasOption("e")) filter_end = Double.parseDouble(cmd.getOptionValue("e"));
				if (cmd.hasOption("n")) filter_max = Long.parseLong(cmd.getOptionValue("n"));
				if (cmd.hasOption("0")) option_nopos = false;
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
				throw new ParseException("No avro file given or invalid arguments.");
			file = cmd.getArgList().get(0);
			out = cmd.getArgList().get(1);
			
		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}
		
		System.out.println("Opening avro file.");
		
		// check if file exists
		try {
			avro = new File(file);
			if(!avro.exists() || avro.isDirectory() || !avro.canRead()) {
				throw new FileNotFoundException("Avro file not found or cannot be read.");
			}
			
			kmlfile = new File(out);
			if(kmlfile.exists() || kmlfile.isDirectory())
				throw new java.io.IOException("KML is a directory or file exists.");
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
		long msgCount = 0, good_pos_cnt = 0, bad_pos_cnt = 0, flights_cnt = 0, err_pos_cnt = 0;
		try {
			DataFileReader<ModeSEncodedMessage> fileReader = new DataFileReader<ModeSEncodedMessage>(avro, datumReader);
			
			System.err.println("Options are:\n" + 
					"\tfile: "+file+"\n"+
					"\ticao24: "+filter_icao24+"\n"+
					"\tstart: "+filter_start+"\n"+
					"\tend: "+filter_end+"\n"+
					"\tmax: "+filter_max+"\n");
			
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
			
			// KML stuff
			Avro2Kml a2k = new Avro2Kml();
			OskyKml kml = a2k.new OskyKml();
			
			mainloop:
			while (fileReader.hasNext()) {
				msgCount++;
				
				// get next record from file
				record = fileReader.next(record);
				
				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start)
					continue;
				
				if (filter_end != null && record.getTimeAtServer()>filter_end)
					continue;
				
				// cleanup decoders every 1.000.000 messages to avoid excessive memory usage
				// therefore, remove decoders which have not been used for more than one hour.
				if (msgCount % 1000000 == 0) {
					List<String> to_remove = new ArrayList<String>();
					for (String key : flights.keySet()) {
						if (flights.get(key).last<record.getTimeAtServer()-3600) {
							to_remove.add(key);
						}
					}

					for (String key : to_remove) {
						// number of flights filter
						if (filter_max != null && kml.getNumberOfFlights()>=filter_max)
							break mainloop;

						if (option_nopos | flights.get(key).coords.size() > 0)
							kml.addFlight(flights.get(key));
						flights.remove(key);
					}
				}
				
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
					flight = a2k.new Flight();
					flight.icao24 = icao24;
					flight.first = record.getTimeAtServer();
					flights.put(icao24, flight);
					++flights_cnt;
				}

				flight.last = record.getTimeAtServer();
				
				if (!flight.serials.contains(record.getSensorSerialNumber()))
					flight.serials.add(record.getSensorSerialNumber());

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
						if (pos.isReasonable()) {
							Coordinate coord = new Coordinate(pos.getLongitude(), pos.getLatitude(),
									// set altitude to 0 if negative... looks nicer in google earth
									pos.getAltitude() != null && pos.getAltitude()>0 ? pos.getAltitude() : 0);
							if (!flight.coords.contains(coord)) { // remove duplicates to safe memory
								flight.coords.add(coord);
								++good_pos_cnt;
							}
						}
						else {
							flight.contains_unreasonable = true;
							++bad_pos_cnt;
						}
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
						if (pos.isReasonable()) {
							Coordinate coord = new Coordinate(pos.getLongitude(), pos.getLatitude(), 0);
							
							if (!flight.coords.contains(coord)) { // remove duplicates to safe memory
								flight.coords.add(coord);
								++good_pos_cnt;
							}
						}
						else {
							flight.contains_unreasonable = true;
							++bad_pos_cnt;
						}
					}
				}
				///////// Identification Messages
				else if (msg.getType() == ModeSReply.subtype.ADSB_IDENTIFICATION) {
					ident = (IdentificationMsg) msg;
					flight.callsign = ident.getIdentity();
				}
			}
			
			// write residual flights to KML
			for (String key : flights.keySet()) {
				// number of flights filter
				if (filter_max != null && kml.getNumberOfFlights()>=filter_max)
					break;
				if (option_nopos | flights.get(key).coords.size() > 0)
					kml.addFlight(flights.get(key));
			}
			
			fileReader.close();
			kml.writeToFile(kmlfile);
			
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
