package emu.hw;

import java.util.logging.Logger;

/**
 * Hardware Interrupt Handler for emuos
 * @author wmosley
 *
 */
@SuppressWarnings("serial")
public class HardwareInterruptException extends Exception {
	
		/**
		 * For tracing
		 */
	Logger trace = Logger.getLogger("emuos");
		/**
		 * A copy of the interupt
		 */
	public HardwareInterruptException() {

	}
}
