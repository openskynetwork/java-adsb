package org.opensky.libadsb.exceptions;

public class MissingInformationException extends Exception {
	private static final long serialVersionUID = -4600948683278132312L;

	public MissingInformationException(String reason) {
		super(reason);
	}
}
