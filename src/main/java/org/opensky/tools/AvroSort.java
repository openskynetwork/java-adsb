package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

/**
 * Sort entries of OpenSky avro files
 * Note: Ordered messages are needed for decoding positions!
 * Warning: make sure you have enough RAM
 * 
 * Outputs avro file with sorted entries.
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class AvroSort {
	
	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"AvroSort [options/filters] input output",
				"\nSort OpenSky AVROs\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		Double filter_start = null, filter_end = null;
		String inpath = null, outpath = null;
		try {
			cmd = parser.parse(opts, args);

			// parse arguments
			try {
				if (cmd.hasOption("s")) filter_start = Double.parseDouble(cmd.getOptionValue("s"));
				if (cmd.hasOption("e")) filter_end = Double.parseDouble(cmd.getOptionValue("e"));
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
				throw new ParseException("Output avro file is missing!");
			inpath = cmd.getArgList().get(0);
			outpath = cmd.getArgList().get(1);

		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}

		// check if file exists
		File avroin = null, avroout = null;
		try {
			// check if output DB exists
			avroout = new File(outpath);
			if (avroout.exists() && !avroout.isDirectory())
				throw new IOException("Output database already exists.");

			// check input file
			avroin = new File(inpath);
			if(!avroin.exists() || avroin.isDirectory() || !avroin.canRead())
				throw new FileNotFoundException("Avro file not found or cannot be read.");
		} catch (IOException e) {
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}

		// AVRO file reader
		DatumReader<ModeSEncodedMessage> datumReader =
				new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		// AVRO file writer
		DatumWriter<ModeSEncodedMessage> datumWriter =
				new SpecificDatumWriter<ModeSEncodedMessage>(ModeSEncodedMessage.class);

		// some counters for statistics
		long msgs_cnt=0, filtered_cnt=0, last_msgs_cnt=0, last_time=0;

		try {
			// open input file
			DataFileReader<ModeSEncodedMessage> fileReader =
					new DataFileReader<ModeSEncodedMessage>(avroin, datumReader);
			
			// open output file
			DataFileWriter<ModeSEncodedMessage> fileWriter =
					new DataFileWriter<ModeSEncodedMessage>(datumWriter);
			fileWriter.create(ModeSEncodedMessage.getClassSchema(), avroout);

			// cache for the avro records
			List<ModeSEncodedMessage> sorted = new ArrayList<ModeSEncodedMessage>();
//			List<ModeSEncodedMessage> sorted = new ArrayList<ModeSEncodedMessage>() {
//			    public boolean add(ModeSEncodedMessage rec) {
//			        int index = Collections.binarySearch(this, rec);
//			        if (index < 0) index = ~index;
//			        super.add(index, mt);
//			        return true;
//			    }
//			};

			// for msg rate
			last_time = System.currentTimeMillis();
			System.err.println("Warning: make sure you have enough main memory. Otherwise, use AvroSplit first.");
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
				ModeSEncodedMessage record = fileReader.next();

				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start) {
					filtered_cnt++;
					continue;
				}
				if (filter_end != null && record.getTimeAtServer()>filter_end) {
					filtered_cnt++;
					continue;
				}
				
				sorted.add(record);
			}
			
			// sort
			Collections.sort(sorted, new Comparator<ModeSEncodedMessage>() {
				@Override
				public int compare(ModeSEncodedMessage o1, ModeSEncodedMessage o2) {
					if (o1 == null) return -1;
					if (o2 == null) return 1;
					return o1.getTimeAtServer().compareTo(o2.getTimeAtServer());
				}
			});
			
			for (ModeSEncodedMessage r : sorted)
				fileWriter.append(r);
			
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
		
		System.err.println("\n\nStatistics:");
		System.err.format("\tTotal messages: %d\n", msgs_cnt);
		System.err.format("\tFiltered messages: %d\n", filtered_cnt);
	}
}
