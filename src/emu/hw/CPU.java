/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.logging.Logger;

import emu.os.SoftwareInterruptException;
import emu.os.SoftwareInterruptException.SoftwareInterruptReason;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class CPU {
	/**
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * Instructions
	 */
	public static final String LOAD = "LR";
	public static final String STORE = "SR";
	public static final String COMPARE = "CR";
	public static final String BRANCH = "BT";
	public static final String GET = "GD";
	public static final String PUT = "PD";
	public static final String HALT = "H";
	/**
	 * Instruction Register
	 */
	String ir;
	/**
	 * General Register
	 */
	String gr;
	/**
	 * Toggle 
	 */
	boolean c;
	/**
	 * Instruction counter
	 */
	int ic;
	/**
	 * System interrupt
	 */
	Interupt si;
	
	/**
	 * The CPU supports three types of interupts
	 * READ, to read data from input
	 * WRITE, to write data to output
	 * TERMINATE to load the next job or terminate execution
	 * @author wmosley
	 *
	 */
	public enum Interupt {
	    READ, WRITE, TERMINATE
	}
	
	/**
	 * get instruction register
	 * @return
	 */
	public String getIr() {
		return ir;
	}

	/**
	 * set instruction register
	 * @param ir
	 */
	public void setIr(String ir) {
		this.ir = ir;
	}

	/**
	 * get toggle
	 * @return
	 */
	public boolean isC() {
		return c;
	}

	/**
	 * set toggle
	 * @param c
	 */
	public void setC(boolean c) {
		this.c = c;
	}
	/** 
	 * get the current instruction count
	 */
	public int getIc() {
		return ic;
	}

	/**
	 * set the instruction count
	 * @param ic
	 */
	public void setIc(int ic) {
		this.ic = ic;
	}

	/**
	 * get Interupt type
	 * @return
	 */
	public Interupt getSi() {
		return si;
	}

	/**
	 * set Interupt type
	 * @param si
	 */
	public void setSi(Interupt si) {
		this.si = si;
	}
	
	/**
	 * get instruction in instruction register
	 * @return
	 */
	public int getIrValue() {
		return Integer.parseInt(ir.substring(2,4));
	}
	
	/**
	 * Execute an instruction
	 * @param memory
	 * @throws HardwareInterruptException
	 * @throws SoftwareInterruptException
	 */
	public void execute(PhysicalMemory memory) throws HardwareInterruptException, SoftwareInterruptException{
		trace.info("execute(): "+toString());
		
		if (ir.startsWith(LOAD)) {
			gr = memory.load(getIrValue());
		}else if (ir.startsWith(STORE)) {
			if (gr == null) {
				throw new SoftwareInterruptException(SoftwareInterruptReason.BADGR);
			} else {
				memory.store(getIrValue(),gr);
			}
		}else if (ir.startsWith(COMPARE)) {
			c = memory.load(getIrValue()).equals(gr);
		}else if (ir.startsWith(BRANCH)) {
			if (c) {
				ic = getIrValue();
			}			
		}else if (ir.startsWith(GET)) {
			si = Interupt.READ;
			throw new HardwareInterruptException();
		}else if (ir.startsWith(PUT)) {
			si = Interupt.WRITE;
			throw new HardwareInterruptException();
		}else if (ir.startsWith(HALT)) {
			si = Interupt.TERMINATE;	
			throw new HardwareInterruptException();
		}else {
			throw new SoftwareInterruptException(SoftwareInterruptReason.OPCODE);
		}
	}

	/**
	 * Load an instruction into IR 
	 * @param memory
	 */
	public void fetch(PhysicalMemory memory) {
		ir = memory.load(ic);
		trace.info("fetch(): "+ir+" from address "+ic);
	}
	
	/**
	 * Increment the instruction counter.
	 */
	public void increment() {
		 ic++;
		 trace.info("increment(): "+ic);
	}
	
	/**
	 * String representation of the the current state.
	 */
	public String toString() {
		return "ic="+ic+" ir="+ir+" gr="+gr+" toggle="+c;
		
	}
	
	/**
	 * Returns state without labels.
	 * @return
	 */
	public String getState() {
		return ic+"    "+ir+"    "+gr+"    "+c;
	}
}
