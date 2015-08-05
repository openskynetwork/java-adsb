package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
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
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.ModeSReply;

/**
 * Prints useful information about OpenSky avro files
 * 
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 *
 */
public class AvroInfo {
	
	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"AvroInfo [options/filters] avro-file",
				"\nOpenSky AVRO to Google Maps KML converter\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	public static void main(String[] args) {
		long start_time = System.currentTimeMillis();
		
		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		
		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		File avro = null;
		String file = null;
		try {
			cmd = parser.parse(opts, args);
			
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
			if(!avro.exists() || avro.isDirectory()) {
				throw new FileNotFoundException("Avro file not found.");
			}

			System.out.println("File info:");
			System.out.println("\tPath: "+avro.getCanonicalPath());
			System.out.println("\tSize: "+avro.length()/Math.pow(1024, 2)+" MBytes");
			System.out.println("\tReadable: "+avro.canRead());
			System.out.println("\tLast modified: "+new Date(avro.lastModified()).toString());
			
			
		} catch (FileNotFoundException e) {
			// avro file not found
			System.err.println("Error: "+e.getMessage()+"\n");
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		DatumReader<ModeSEncodedMessage> datumReader = new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		long msgCount = 0;
		try {
			DataFileReader<ModeSEncodedMessage> fileReader = new DataFileReader<ModeSEncodedMessage>(avro, datumReader);

			Schema schema = fileReader.getSchema();
			System.out.println("Schema info:");
			System.out.println("\tType: "+schema.getType());
			System.out.println("\tName: "+schema.getName());
			System.out.println("\tNamespace: "+schema.getNamespace());
			System.out.println("\tFields ("+schema.getFields().size()+"): ");
			for (Field field : schema.getFields())
				System.out.println("\t\t"+field);
			
			System.out.print("Counting entries: ");
			ModeSEncodedMessage record = new ModeSEncodedMessage();
			double min_time = Double.MAX_VALUE, max_time = Double.MIN_VALUE;
			
			HashMap<Integer, Long> sensors = new HashMap<Integer, Long>();
			int serial; Long serial_cnt;
			while (fileReader.hasNext()) {
				// get next record from file
				record = fileReader.next(record);
				msgCount++;
				
				if (record.getTimeAtServer() < min_time)
					min_time = record.getTimeAtServer();
				if (record.getTimeAtServer() > max_time)
					max_time = record.getTimeAtServer();
				
				serial = record.getSensorSerialNumber();
				serial_cnt = sensors.get(serial);
				sensors.put(serial, serial_cnt != null ? serial_cnt+1 : 0L);
			}
			System.out.println(msgCount);
			System.out.println("Earliest entry: "+new Date((long)(min_time*1000)).toString());
			System.out.println("Latest entry: "+new Date((long)(max_time*1000)).toString());
			
			System.out.println("Sensor statistics:");
			for (int key : sensors.keySet())
				System.out.println("\t"+key+": "+sensors.get(key));
			fileReader.close();
			
		} catch (IOException e) {
			// error while trying to read file
			System.err.println("Could not read file: "+e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			// something went wrong
			System.err.println("Something went wrong: "+e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("\nTime to process complete file: "+(System.currentTimeMillis()-start_time)/1000.0+" seconds");
	}
}
