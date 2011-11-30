/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class CPUState implements Cloneable {
	/**
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * Instruction Register
	 */
	String ir;
	/**
	 * General Register
	 */
	String gr;
	/**
	 * Page Table Register
	 */
	int ptr;
	/**
	 * Page Table Length
	 */
	int ptl;
	/**
	 * Toggle 
	 */
	boolean c;
	/**
	 * Instruction counter
	 */
	int ic;
	/**
	 * cpu clock
	 */
	int clock;
	/**
	 * System interrupt
	 */
	Interrupt si;
	/**
	 * Program interrupt	
	 */
	Interrupt pi;
	/**
	 * time interrupt
	 */
	Interrupt ti;
	/** 
	 * input and output interrupt
	 */
	Interrupt ioi;
	/**
	 * All interrupts are grouped together. Their types are verified upon setting when set.
	 * value represents what is specified in the phase 2 doc
	 * retval is what a function will return if an interrupt is thrown
	 * type is the type of interrupt 
	 * errorCode is the error message to which the interrupt corresponds 
	 * @author wmosley
	 *
	 */
	public enum Interrupt {
		CLEAR           ( 0,515, InterruptType.MASTER    , -1),
		WRONGTYPE       (-1,525, InterruptType.MASTER    , -1),
	    READ            ( 1,516, InterruptType.SUPERVISOR, -1),
	    WRITE           ( 2,517, InterruptType.SUPERVISOR, -1),
	    TERMINATE       ( 3,518, InterruptType.SUPERVISOR, -1),
		UNKNOWN         (-1,519, InterruptType.DEFAULT   , -1),
		IO_CHANNEL_1    ( 1,521, InterruptType.IO        , -1),
		IO_CHANNEL_2    ( 2,522, InterruptType.IO        , -1),
		IO_CHANNEL_12   ( 3,523, InterruptType.IO        , -1),
		IO_CHANNEL_3    ( 4,524, InterruptType.IO        , -1),
		IO_CHANNEL_13   ( 5,525, InterruptType.IO        , -1),
		IO_CHANNEL_23   ( 6,526, InterruptType.IO        , -1),
		IO_CHANNEL_123  ( 7,527, InterruptType.IO        , -1),
		TIME_QUANTUM    ( 1,550, InterruptType.TIME      , -1),
		TIME_ERROR      ( 2,528, InterruptType.TIME      , 3),
		OPERATION_ERROR ( 1,529, InterruptType.PROGRAM   , 4),
		OPERAND_ERROR   ( 2,530, InterruptType.PROGRAM   , 5),
		PAGE_FAULT      ( 3,531, InterruptType.PROGRAM   , 6);
		private int value;
		private int retval;
		InterruptType type;
		int errorCode;

		Interrupt (int value, int retval,InterruptType type, int errorCode) {
			this.value = value;
			this.retval = retval;
			this.type = type;
			this.errorCode = errorCode;
		}
		public int getValue() {
			return value;
		}
		public int getErrorCode(){
			return errorCode;
		}
		public InterruptType getType(){
			return type;
		}
		public int getRetval(){
			return retval;
		}
		public static Interrupt set(int irValue) {
			for (Interrupt i: values()) {
				if (i.getRetval() == irValue) return i;
			}
			return CLEAR;
		}
		public static Interrupt getIOi(int ioiValue) {
			for (Interrupt i: values()) {
				if (i.getType().equals(InterruptType.IO) && 
						i.getValue() == ioiValue) return i;
			}
			return CLEAR;
		}
		
	}
	
	/**
	 * Initialize CPUState
	 */
	public CPUState() {
		clearInterrupts();
		trace.info(dumpInterrupts());
	}
	
	/**
	 * 
	 * @return
	 */
	public String dumpInterrupts() {

		return "si="+getSi().getValue()+" " +
				"pi="+getPi().getValue()+" " +
				"ti="+getTi().getValue()+" " +
				"ioi="+getIOi().getValue();
	}

	/**
	 * All the valid interrupt types
	 * @author wmosley
	 *
	 */
	public enum InterruptType {
		MASTER,
		SUPERVISOR,
		PROGRAM,
		TIME,
		IO,
		DEFAULT;
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
	 * Get C as T or F
	 * @return
	 */
	public String getCString() {
		return Boolean.toString(c).substring(0,1).toUpperCase();
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
	 * get the cpu clock
	 */
	public int getClock() {
		return clock;
	}

	/**
	 * get Interupt type
	 * @return
	 */
	public Interrupt getSi() {
		return si;
	}

	/**
	 * set Interrupt type
	 * @param si
	 */
	public void setSi(Interrupt si) {
		if (si.getType().equals(InterruptType.SUPERVISOR)
				|| si.getType().equals(InterruptType.MASTER))
			this.si = si;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Supervisor Interrupt: " +si);
			this.pi = Interrupt.WRONGTYPE;
		}
	}
	
	/**
	 * set supervisor interrupt based on integer value
	 * @param value
	 */
	public void setSi(int value) {
		Interrupt i = Interrupt.set(value);
		setSi(i);
	}
	
	/**
	 * get ProgramInterrupt type
	 * @return
	 */
	public Interrupt getPi() {
		return pi;
	}

	/**
	 * set ProgramInterrupt type
	 * @param si
	 */
	public void setPi(Interrupt pi) {
		if (pi.getType().equals(InterruptType.PROGRAM)
				|| pi.getType().equals(InterruptType.MASTER))
			this.pi = pi;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Program Interrupt: " +pi);
			this.pi = Interrupt.WRONGTYPE;
		}
	}
	
	/** 
	 * set program interrupt based on integer value
	 * @param value
	 */
	public void setPi(int value) {
		Interrupt i = Interrupt.set(value);
		setPi(i);
	}
	
	/**
	 * get Interrupt type
	 * @return
	 */
	public Interrupt getTi() {
		return ti;
	}

	/**
	 * set TimeInterrupt type
	 * @param si
	 */
	public void setTi(Interrupt ti) {
		if (ti.getType().equals(InterruptType.TIME)
				|| ti.getType().equals(InterruptType.MASTER))
			this.ti = ti;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Time Interrupt: " +ti);
			this.ti = Interrupt.WRONGTYPE;
		}
	}
	
	/**
	 * set time interrupt based on integer value
	 * @param value
	 */
	public void setTi(int value) {
		Interrupt i = Interrupt.set(value);
		setTi(i);
	}
	
	/**
	 * get Interrupt type
	 * @return
	 */
	public Interrupt getIOi() {
		return ioi;
	}

	/**
	 * set TimeInterrupt type
	 * @param si
	 */
	public void setIOi(Interrupt ioi) {
		if (ioi.getType().equals(InterruptType.IO)
				|| ioi.getType().equals(InterruptType.MASTER))
			this.ioi = ioi;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Time Interrupt: " +ioi);
			this.ioi = Interrupt.WRONGTYPE;
		}
	}
	
	/**
	 * Set the IO interrupt based on integer value
	 * @param value
	 */
	public void setIOi(int value) {
		trace.info("setting ioi="+value);
		int newVal = ioi.getValue() | value;
		setIOi(Interrupt.getIOi(newVal));
	}
	
	/**
	 * Clear the IO interrupt based on integer value
	 * @param value
	 */
	public void clearIOi(int value) {
		trace.info("clearing ioi="+value);
		int newVal = (Interrupt.IO_CHANNEL_123.getValue() - value) & ioi.getValue();
		setIOi(Interrupt.getIOi(newVal));
	}
	
	/**
	 * get instruction in instruction register
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public int getOperand() {
		
		int retval = -1; 
		try {
			retval = Integer.parseInt(ir.substring(2,4));
			trace.fine("operand: "+retval);
		} catch (NumberFormatException e) {
			trace.severe("invalid operand format:"+ir.substring(2,4));
		}
			
		if (retval < 0 || retval > 100){
			retval = Interrupt.OPERAND_ERROR.getRetval();
		}
		return retval;
	}
	
	/**
	 * String representation of the the current state.
	 */
	public String toString() {
		return "ptr="+ptr+" ptl="+ptl+" ic="+ic+" ir="+ir+" gr="+gr+" c="+getCString()+" "+dumpInterrupts();
		
	}
	
	/**
	 * Returns state without labels.
	 * @return
	 */
	public String getState() {
		return ic+"    "+ir+"    "+gr+"    "+getCString();
	}
	
	public int getPtr() {
		return ptr;
	}

	public void setPtr(int ptr) {
		this.ptr = ptr;
	}
	

	public int getPtl() {
		return ptl;
	}

	public void setPtl(int ptl) {
		this.ptl = ptl;
	}
	
	public int incrementPtl() {
		return ptl++;
	}

	/**
	 * Clears all interrupts.
	 */
	public void clearInterrupts() {
		si = Interrupt.CLEAR;
		ti = Interrupt.CLEAR;
		pi = Interrupt.CLEAR;
		ioi = Interrupt.CLEAR;
	}


	@Override
	public Object clone() throws CloneNotSupportedException {
		// TODO Auto-generated method stub
		return super.clone();
	}
	
	/**
	 * Restore the CPU based on the given CPU instance
	 * @param cpu
	 */
	public void restore(CPUState cpu) {
		this.c = cpu.c;
		this.ir = cpu.ir;
		this.gr = cpu.gr;
		this.ic = cpu.ic;
		this.ptr = cpu.ptr;
		this.ptl = cpu.ptl;
		this.si = cpu.si;
		this.ti = cpu.ti;
		this.pi = cpu.pi;
		this.ioi = cpu.ioi;
	}
}
