package org.opensky.libadsb.exceptions;

public class BadFormatException extends Exception {
	private static final long serialVersionUID = 5630832543039853589L;

	private String msg;
	private String reason;
	
	public BadFormatException(String reason, String message) {
		super(reason);
		this.msg = message;
		this.reason = reason;
	}

	@Override
	public String getMessage() {
		return "Message '" + this.msg + "' has an illegal format: "
				+ this.reason;
	}
}
