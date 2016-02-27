package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

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
import org.opensky.libadsb.msgs.ModeSReply;

/**
 * Prints useful information about OpenSky avro files
 * 
 * @author Matthias Schäfer <schaefer@opensky-network.org>
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
		opts.addOption("c", "count", false, "count message formats" );
		opts.addOption("p", "parity", false, "ignore messages with bad parity" );
		opts.addOption("v", "verbose", false, "print every message" );
		
		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		File avro = null;
		String file = null;
		boolean option_count = false, option_parity = false, verbose = false;
		try {
			cmd = parser.parse(opts, args);
			
			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}

			if (cmd.hasOption("c")) option_count = true;
			if (cmd.hasOption("p")) option_parity = true;
			if (cmd.hasOption("v")) verbose = true;
			
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
			long[] adsb_cnt = new long[32];
			long[] modes_cnt = new long[32];
			ModeSReply msg;
			while (fileReader.hasNext()) {
				// get next record from file
				record = fileReader.next(record);
				
				//System.out.println(record.toString());
				
				if (option_parity || option_count) {
					try {
						msg = new ModeSReply(record.getRawMessage().toString());
						if (option_parity && !msg.checkParity()) continue;
						modes_cnt[msg.getDownlinkFormat()]++;
						if (msg.getDownlinkFormat() == 17 || msg.getDownlinkFormat() == 18) {
							adsb_cnt[Short.parseShort(record.getRawMessage().toString().substring(8, 10), 16)>>3]++;
						}
					} catch (Exception e) {
						System.out.println("Caught exception: "+e.getMessage());
					}
				}
				
				if (verbose)
					System.out.println(Decoder.genericDecoder(record.getRawMessage().toString()).toString());
				
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
			
			System.out.println("Counts per Mode S downlink format:");
			for (int i = 0; i<modes_cnt.length; i++)
				if (modes_cnt[i]>0) System.out.println("    Format "+i+": "+modes_cnt[i]);

			System.out.println("Counts per ADS-B format type code:");
			for (int i = 0; i<adsb_cnt.length; i++)
				if (adsb_cnt[i]>0) System.out.println("    Code "+i+": "+adsb_cnt[i]);
			
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
