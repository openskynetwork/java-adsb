package org.opensky.example;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.avro.Schema.Field;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificDatumReader;

/**
 * OpenSky AVRO example decoder
 * 
 * This class can be used to read and decode raw Avro files as provided by the
 * OpenSky Network. As raw data can be quite big the decoder expects you to
 * provide the maximum number of message to be decoded. All information is 
 * printed to stdout.
 * @author Markus Fuchs <fuchs@sero-systems.de>
 */
public class OskySampleReader {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: OskySampleReader <input file> <#records to read>");
			System.exit(1);
		}
		String filename = args[0];
		int numRead = Integer.parseInt(args[1]);

		// open AVRO file with pre-compiled Schema class
		File f = new File(filename);
		DatumReader<ModeSEncodedMessage> datumReader = new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		DataFileReader<ModeSEncodedMessage> fileReader = new DataFileReader<ModeSEncodedMessage>(
				f, datumReader);
		
		ExampleDecoder decoder = new ExampleDecoder();

		// allocate record for reuse to prevent garbage collection overhead
		ModeSEncodedMessage record = new ModeSEncodedMessage();
		
		int minTime = Integer.MAX_VALUE;
		int maxTime = 0;
		long msgCount = 0;
		Set<Integer> serials = new HashSet<Integer>();
		while (fileReader.hasNext()) {
			// get next record from file
			record = fileReader.next(record);
			
			msgCount++;
			serials.add(record.getSensorSerialNumber());
			maxTime = Math.max(record.getTimeAtServer().intValue(), maxTime);
			minTime = Math.min(record.getTimeAtServer().intValue(), minTime);
			
			if (--numRead >= 0) {
				System.out.println("record #" + msgCount + " (raw AVRO)");
				for (Field field : record.getSchema().getFields()) {
					if (record.get(field.name()) != null)
						System.out.printf("\t%-20s %s\n", field.name() + ":", 
								record.get(field.name()));
				}
				System.out.println("record #" + msgCount + " (decoded)");
				decoder.decodeMsg(record.getRawMessage().toString());
			}
			
		}
		fileReader.close();
		
		// print stats
		System.out.println("");
		System.out.println("File Statistics");
		System.out.printf("\t%-20s %s\n", "start time:", new Date(((long) minTime) * 1000l).toString());
		System.out.printf("\t%-20s %s\n", "end time:", new Date(((long) maxTime) * 1000l).toString());
		System.out.printf("\t%-20s %d\n", "total message count:", msgCount);
		System.out.printf("\t%-20s %d\n", "sensor count:", serials.size());
	}
}
