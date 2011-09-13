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
	private static final String ERR2 = "Maximum lines exceeded";
	private static final String ERR1 = "Maximum time exceeded";
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
		if (ErrCode == 1)
			return MESSAGE + ERR1;
		else 
			return MESSAGE + ERR2;
	}

}
