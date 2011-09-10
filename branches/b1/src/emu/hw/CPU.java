package emu.hw;

import java.util.logging.Logger;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
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
	    READ, WRITE, TERMINATE, CONTINUE
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
	
	public void execute(MMU memory) {
		trace.info("execute(): ir = "+ir+" gr = "+gr+" toggle = "+c);
		if (ir.startsWith(LOAD)) {
			gr = memory.load(getIrValue());
		}else if (ir.startsWith(STORE)) {
			memory.store(getIrValue(),gr);
		}else if (ir.startsWith(COMPARE)) {
			if (memory.load(getIrValue()).equals(gr)){
				c = Boolean.TRUE;
			}
		}else if (ir.startsWith(BRANCH)) {
			if (c) {
				ic = getIrValue();
			}			
		}else if (ir.startsWith(GET)) {
			si = Interupt.READ;			
		}else if (ir.startsWith(PUT)) {
			si = Interupt.WRITE;
		}else if (ir.startsWith(HALT)) {
			si = Interupt.TERMINATE;			
		}
	}

	public void fetch(MMU memory) {
		ir = memory.load(ic);
		trace.info("fetch(): "+ir+" from address "+ic);
	}

	public void increment() {
		 ic++;
		 trace.info("increment(): "+ic);
	}
}
