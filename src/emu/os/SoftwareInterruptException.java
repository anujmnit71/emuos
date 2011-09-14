package emu.os;

import java.util.logging.Logger;

/**
 * Software Interrupt Handler for emuos
 * @author wmosley
 *
 */
@SuppressWarnings("serial")
public class SoftwareInterruptException extends Exception {

	/**
	 * For tracing
	 */
	Logger trace = Logger.getLogger("emuos");
	/**
	 * Error Code
	 * 1. Maximum time exceeded
	 * 2. Maximum lines exceeded
	 */
	SoftwareInterruptReason ErrValue; 
	public enum SoftwareInterruptReason {
	    MAXTIME ("Max time exceeded"),
	    MAXLINES("Max output lines exceeded"),
	    BADGR   ("Invalid GR value"),
	    OPCODE  ("Invalid OPCODE"),
	    UNKNOWN ("Unknown Error");
	    
	    private final String reason;
	    SoftwareInterruptReason(String message) {
	    	reason = message;
	    }
	    private String reason() {return reason;}
	}
	/**
	 * generic message string for Software Interrupt
	 */
	private static final String MESSAGE = "AbEnd: ";
	/**
	 * 
	 * @param code
	 */
	public SoftwareInterruptException(SoftwareInterruptReason reason) {
		if (reason == null)
			reason = SoftwareInterruptReason.UNKNOWN;
		ErrValue = reason;
	}

	public String getMessage() {
		String retval = MESSAGE;
		retval += ErrValue.reason();
		return retval;
	}

}
