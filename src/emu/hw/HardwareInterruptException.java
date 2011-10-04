/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.logging.Logger;

/**
 * Hardware Interrupt Handler for emuos is used as a mechanism for
 * throwing all hardware interrupt
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
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
