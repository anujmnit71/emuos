/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.os;

import java.util.logging.Logger;

/**
 * Software Interrupt Handler for emuos
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
@SuppressWarnings("serial")
public class SoftwareInterruptException extends Exception {

	/**
	 * For tracing
	 */
	Logger trace = Logger.getLogger("emuos");
	/**
	 * SoftwareInterruptReason contains the different interrupt types
	 * along with a brief explanation.
	 */
	SoftwareInterruptReason ErrValue; 
	/**
	 * Enumeration of the different exception reasons. 
	 */
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

	/**
	 * 
	 */
	public String getMessage() {
		String retval = MESSAGE;
		retval += ErrValue.reason();
		return retval;
	}

}
