package emu.hw;

import java.util.logging.Logger;

import emu.hw.CPU.Interupt;

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
	Interupt si;
	public HardwareInterruptException(Interupt si) {
	    this.si = si;
	}
		
	public Interupt getInterupt() {
		return si;
	}

}
