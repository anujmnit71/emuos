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
	int ErrCode;
	/**
	 * generic message string for Software Interrupt
	 */
	private static final String MESSAGE = "Abnormal Termination: ";
	private static final String ERR1 = "Maximum time exceeded";
	private static final String ERR2 = "Maximum lines exceeded";
	private static final String ERR3 = "Invalid GR value";
	private static final String DEFAULT = "Unknown Error";
	/**
	 * 
	 * @param code
	 */
	public SoftwareInterruptException(int code) {
		ErrCode = code;
	}
	
	public int getAbEndCode() {
		return ErrCode;
	}
	
	public String getMessage() {
		String retval = MESSAGE;
		switch (ErrCode) {
		case 1:
			retval += ERR1;
			break;
		case 2:
			retval += ERR2;
			break;
		case 3:
			retval += ERR3;
			break;
			default:
			retval += DEFAULT;					
		}
		return retval;
	}

}
