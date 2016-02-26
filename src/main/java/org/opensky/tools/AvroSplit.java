package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
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
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.ModeSReply;

/**
 * Read (possibly multiple) OpenSky Avro files and split them to n files.
 * The tool ensures that all messages of the same aircraft are
 * in one file! This is needed for position decoding.
 * 
 * This tool is useful if you have messages from the same flight distributed
 * to multiple files or if you have one big file and want to have smaller files.
 * 
 * @author Matthias Schäfer <schaefer@opensky-network.org>
 *
 */
public class AvroSplit {

	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"AvroSplit [options/filters] input1 [input2 ...] -o output_prefix",
				"\nSplit OpenSky AVROs\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("o", "output", true, "prefix for output files (number of file will be appended)");
		opts.addOption("n", "number", true, "number of output files (default: 1)");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		Integer num_files = 1;
		String outpath = null;
		List<String> inpaths = null;
		try {
			cmd = parser.parse(opts, args);

			// parse arguments
			try {
				if (cmd.hasOption("o")) outpath = cmd.getOptionValue("o");
				else throw new ParseException("Need output prefix!");
				if (cmd.hasOption("n")) num_files = Integer.parseInt(cmd.getOptionValue("n"));
			} catch (NumberFormatException e) {
				throw new ParseException("Invalid arguments: "+e.getMessage());
			}

			if (num_files > 256)
				throw new ParseException("At most 256 output files allowed!");
			else if (num_files < 1)
				throw new ParseException("At least 1 output files required!");

			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}

			// get filename
			if (cmd.getArgList().size() == 0)
				throw new ParseException("Input files are missing!");
			inpaths = cmd.getArgList();
		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}

		// check if file exists
		List<File> avroin = new ArrayList<File>(), avroout = new ArrayList<File>();
		try {
			// check if output paths exist
			for (int i=1; i<=num_files; ++i) {
				avroout.add(new File(outpath+i+".avro"));
				if (avroout.get(i-1).exists() && !avroout.get(i-1).isDirectory())
					throw new IOException("Output database already exists.");
			}

			// check input files
			File tmp;
			for (String path : inpaths) {
				tmp = new File(path);
				if(!tmp.exists() || tmp.isDirectory() || !tmp.canRead())
					throw new FileNotFoundException("Avro file not found or cannot be read.");
				avroin.add(tmp);
			}
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
		long[] out_cnt = new long[num_files], in_cnt = new long[avroin.size()];
		long msgs_cnt = 0;
		try {
			// open output files
			List<DataFileWriter<ModeSEncodedMessage>> writers = new ArrayList<DataFileWriter<ModeSEncodedMessage>>();
			DataFileWriter<ModeSEncodedMessage> tmp;
			for (File file : avroout) {
				tmp = new DataFileWriter<ModeSEncodedMessage>(datumWriter);
				tmp.create(ModeSEncodedMessage.getClassSchema(), file);
				writers.add(tmp);
			}

			long last_time = System.currentTimeMillis(), last_msgs_cnt = 0;
			DataFileReader<ModeSEncodedMessage> fileReader;
			int out_file;
			// iterate over input files
			for (int i = 0; i<avroin.size(); ++i) {
				System.err.format("\nOpening %s.\n", inpaths.get(i));
				fileReader = new DataFileReader<ModeSEncodedMessage>(avroin.get(i), datumReader);
				while (fileReader.hasNext()) {
					// count messages
					++msgs_cnt;
					++in_cnt[i];

					// print processing rate
					if (System.currentTimeMillis() - last_time > 1000) {
						System.err.format("\r%6d msgs/s", msgs_cnt-last_msgs_cnt);
						last_time = System.currentTimeMillis();
						last_msgs_cnt = msgs_cnt;
					}

					// get next record from file
					ModeSEncodedMessage record = fileReader.next();

					// determine file
					try {
						out_file = (new ModeSReply(record.getRawMessage().toString()).getIcao24()[2]&0xFF) % num_files; // max 256 files!
						++out_cnt[out_file];
						writers.get(out_file).append(record);
					} catch (BadFormatException e) {
						System.err.println("\nSkipped bad formatted messages.");
					}
				}
				fileReader.close();
			}
			// close all files
			for (DataFileWriter<ModeSEncodedMessage> writer : writers)
				writer.close();

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
		System.err.println("\tCounts per input file:");
		for (int i=0; i<inpaths.size(); ++i)
			System.err.format("\t\t%s: %d\n", inpaths.get(i), in_cnt[i]);
		System.err.println("\tCounts per output file:");
		for (int i=0; i<num_files; ++i)
			System.err.format("\t\t%s: %d\n", outpath+i+".avro", out_cnt[i]);
	}
}
