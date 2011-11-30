/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.logging.Level;
import java.util.logging.Logger;

import emu.hw.CPUState.Interrupt;
import emu.hw.CPUState.InterruptType;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class CPU implements Cloneable {
	/**
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * Instructions
	 */
	public static final String LOAD    = "LR";
	public static final String STORE   = "SR";
	public static final String COMPARE = "CR";
	public static final String BRANCH  = "BT";
	public static final String GET     = "GD";
	public static final String PUT     = "PD";
	public static final String HALT    = "H   ";
	/**
	 * Self Reference 
	 */
	static CPU ref;
	/**
	 * 
	 */
	CPUState state;
//	/**
//	 * Instruction Register
//	 */
//	String ir;
//	/**
//	 * General Register
//	 */
//	String gr;
//	/**
//	 * Page Table Register
//	 */
//	int ptr;
//	/**
//	 * Page Table Length
//	 */
//	int ptl;
//	/**
//	 * Toggle 
//	 */
//	boolean c;
//	/**
//	 * Instruction counter
//	 */
//	int ic;
//	/**
//	 * cpu clock
//	 */
//	int clock;
//	/**
//	 * System interrupt
//	 */
//	Interrupt si;
//	/**
//	 * Program interrupt	
//	 */
//	Interrupt pi;
//	/**
//	 * time interrupt
//	 */
//	Interrupt ti;
//	/** 
//	 * input and output interrupt
//	 */
//	Interrupt ioi;
	/**
	 * Memory Management Unit
	 */
	private MMU mmu;
	
//	/**
//	 * All interrupts are grouped together. Their types are verified upon setting when set.
//	 * value represents what is specified in the phase 2 doc
//	 * retval is what a function will return if an interrupt is thrown
//	 * type is the type of interrupt 
//	 * errorCode is the error message to which the interrupt corresponds 
//	 * @author wmosley
//	 *
//	 */
//	public enum Interrupt {
//		CLEAR           ( 0,515, InterruptType.MASTER    , -1),
//		WRONGTYPE       (-1,525, InterruptType.MASTER    , -1),
//	    READ            ( 1,516, InterruptType.SUPERVISOR, -1),
//	    WRITE           ( 2,517, InterruptType.SUPERVISOR, -1),
//	    TERMINATE       ( 3,518, InterruptType.SUPERVISOR, -1),
//		UNKNOWN         (-1,519, InterruptType.DEFAULT   , -1),
//		IO_CHANNEL_1    ( 1,521, InterruptType.IO        , -1),
//		IO_CHANNEL_2    ( 2,522, InterruptType.IO        , -1),
//		IO_CHANNEL_12   ( 3,523, InterruptType.IO        , -1),
//		IO_CHANNEL_3    ( 4,524, InterruptType.IO        , -1),
//		IO_CHANNEL_13   ( 5,525, InterruptType.IO        , -1),
//		IO_CHANNEL_23   ( 6,526, InterruptType.IO        , -1),
//		IO_CHANNEL_123  ( 7,527, InterruptType.IO        , -1),
//		TIME_ERROR      ( 2,528, InterruptType.TIME      , 3),
//		OPERATION_ERROR ( 1,529, InterruptType.PROGRAM   , 4),
//		OPERAND_ERROR   ( 2,530, InterruptType.PROGRAM   , 5),
//		PAGE_FAULT      ( 3,531, InterruptType.PROGRAM   , 6);
//		private int value;
//		private int retval;
//		InterruptType type;
//		int errorCode;
//
//		Interrupt (int value, int retval,InterruptType type, int errorCode) {
//			this.value = value;
//			this.retval = retval;
//			this.type = type;
//			this.errorCode = errorCode;
//		}
//		public int getValue() {
//			return value;
//		}
//		public int getErrorCode(){
//			return errorCode;
//		}
//		public InterruptType getType(){
//			return type;
//		}
//		public int getRetval(){
//			return retval;
//		}
//		public static Interrupt set(int irValue) {
//			for (Interrupt i: values()) {
//				if (i.getRetval() == irValue) return i;
//			}
//			return CLEAR;
//		}
//		public static Interrupt getIOi(int ioiValue) {
//			for (Interrupt i: values()) {
//				if (i.getType().equals(InterruptType.IO) && 
//						i.getValue() == ioiValue) return i;
//			}
//			return CLEAR;
//		}
//		
//	}
//	
	/**
	 * Initialize CPU
	 */
	private CPU() {
		state = new CPUState();
		mmu = MMU.getInstance();
		trace.info(dumpInterrupts());
	}
	
	/**
	 * 
	 * @return
	 */
	public String dumpInterrupts() {

		return state.dumpInterrupts();
	}

	/**
	 * 
	 * @return
	 */
	public static CPU getInstance() {

		if (ref == null) {
			ref = new CPU();
		}

		return ref;
		
	}
//	/**
//	 * All the valid interrupt types
//	 * @author wmosley
//	 *
//	 */
//	public enum InterruptType {
//		MASTER,
//		SUPERVISOR,
//		PROGRAM,
//		TIME,
//		IO,
//		DEFAULT;
//	}
	
	/**
	 * get instruction register
	 * @return
	 */
	public String getIr() {
		return state.ir;
	}

	/**
	 * set instruction register
	 * @param ir
	 */
	public void setIr(String ir) {
		state.ir = ir;
	}

	/**
	 * get toggle
	 * @return
	 */
	public boolean isC() {
		return state.c;
	}
	
	/**
	 * Get C as T or F
	 * @return
	 */
	public String getCString() {
		return Boolean.toString(state.c).substring(0,1).toUpperCase();
	}

	/**
	 * set toggle
	 * @param c
	 */
	public void setC(boolean c) {
		state.c = c;
	}
	/** 
	 * get the current instruction count
	 */
	public int getIc() {
		return state.ic;
	}

	/**
	 * set the instruction count
	 * @param ic
	 */
	public void setIc(int ic) {
		this.state.ic = ic;
	}
	/**
	 * get the cpu clock
	 */
	public int getClock() {
		return state.clock;
	}

	/**
	 * get Interupt type
	 * @return
	 */
	public Interrupt getSi() {
		return state.si;
	}

	/**
	 * set Interrupt type
	 * @param si
	 */
	public void setSi(Interrupt si) {
		if (si.getType().equals(InterruptType.SUPERVISOR)
				|| si.getType().equals(InterruptType.MASTER))
			state.si = si;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Supervisor Interrupt: " +si);
			state.pi = Interrupt.WRONGTYPE;
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
		return state.pi;
	}

	/**
	 * set ProgramInterrupt type
	 * @param si
	 */
	public void setPi(Interrupt pi) {
		if (pi.getType().equals(InterruptType.PROGRAM)
				|| pi.getType().equals(InterruptType.MASTER))
			state.pi = pi;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Program Interrupt: " +pi);
			state.pi = Interrupt.WRONGTYPE;
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
		return state.ti;
	}

	/**
	 * set TimeInterrupt type
	 * @param si
	 */
	public void setTi(Interrupt ti) {
		if (ti.getType().equals(InterruptType.TIME)
				|| ti.getType().equals(InterruptType.MASTER))
			state.ti = ti;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for Time Interrupt: " +ti);
			state.ti = Interrupt.WRONGTYPE;
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
		return state.ioi;
	}

	/**
	 * set TimeInterrupt type
	 * @param si
	 */
	public void setIOi(Interrupt ioi) {
		if (ioi.getType().equals(InterruptType.IO)
				|| ioi.getType().equals(InterruptType.MASTER) 
				|| ioi.equals(Interrupt.CLEAR))
			state.ioi = ioi;
		else {
			trace.log(Level.SEVERE,"You tried to set the incorrect interrupt for IO Interrupt: " +ioi);
			state.ioi = Interrupt.WRONGTYPE;
		}
	}
	
	/**
	 * Set the IO interrupt based on integer value
	 * @param value
	 */
	public void setIOi(int value) {
		trace.info("setting ioi="+value);
		int newVal = state.ioi.getValue() | value;
		setIOi(Interrupt.getIOi(newVal));
	}
	
	/**
	 * Clear the IO interrupt based on integer value
	 * @param value
	 */
	public void clearIOi(int value) {
		trace.info("clearing ioi="+value);
		int newVal = (Interrupt.IO_CHANNEL_123.getValue() - value) & state.ioi.getValue();
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
			retval = Integer.parseInt(state.ir.substring(2,4));
			trace.fine("operand: "+retval);
		} catch (NumberFormatException e) {
			trace.severe("invalid operand format:"+state.ir.substring(2,4));
		}
			
		if (retval < 0 || retval > 100){
			retval = Interrupt.OPERAND_ERROR.getRetval();
		}
		return retval;
	}
	
	/**
	 * Execute an instruction
	 * @param memory
	 * @throws HardwareInterruptException
	 * @throws SoftwareInterruptException
	 */
	public void execute() throws HardwareInterruptException {
		trace.finer("-->");
		trace.info(toString());
		state.clock++;
		int logicalAddr = 0;
		if (state.ir == null) {
			trace.info("noop");
		}
		else if (state.ir.startsWith(LOAD)) {
			logicalAddr = getOperand();
			state.pi = Interrupt.set(logicalAddr);
			if (state.pi == Interrupt.CLEAR) {
				state.gr = mmu.load(state.ptr,logicalAddr);
				trace.info("r<-"+state.gr);
			}
		}else if (state.ir.startsWith(STORE)) {
			if (state.gr == null)
				state.pi = Interrupt.OPERAND_ERROR;
			else {
				logicalAddr = getOperand();
				state.pi = Interrupt.set(logicalAddr);
					if (state.pi == Interrupt.CLEAR)
						mmu.store(state.ptr,logicalAddr,state.gr);
			}
		}else if (state.ir.startsWith(COMPARE)) {
			logicalAddr = getOperand();
			state.pi = Interrupt.set(logicalAddr);
				if (state.pi == Interrupt.CLEAR) {
					state.c = mmu.load(state.ptr,logicalAddr).equals(state.gr);
					trace.info("c<-"+state.c);
				}
		}else if (state.ir.startsWith(BRANCH)) {
			if (state.c) {
				logicalAddr = getOperand();
				state.pi = Interrupt.set(logicalAddr);
					if (state.pi == Interrupt.CLEAR)
						state.ic = logicalAddr;
			}			
		}else if (state.ir.startsWith(GET)) {
			state.si = Interrupt.READ;
			//trace.info("si<-"+Interrupt.READ.getValue());
		}else if (state.ir.startsWith(PUT)) {
			state.si = Interrupt.WRITE;
			//trace.info("<-"+Interrupt.WRITE.getValue());
		}else if (state.ir.startsWith(HALT)) {
			state.si = Interrupt.TERMINATE;	
			//trace.info("state.si<-"+Interrupt.TERMINATE.getValue());
		}else {
			trace.severe("unknown operation:"+state.ir);
			state.pi = Interrupt.OPERATION_ERROR;
		}
		
		/*
		 * wait until all instructions have been handled before throwing
		 * exception
		 */

		trace.info(toString());
		if (state.si != Interrupt.CLEAR
				|| state.pi != Interrupt.CLEAR){
			trace.finer("<--");
			throw new HardwareInterruptException();
		}
		trace.finer("<--");
	}
	
	/**
	 * Load an instruction into IR 
	 * @param memory
	 * @throws HardwareInterruptException 
	 */
	public void fetch() throws HardwareInterruptException {
		state.ir = mmu.load(state.ptr,state.ic);
		trace.info(state.ir+" from logical address "+state.ic);
	}
	
	/**
	 * Increment the instruction counter.
	 */
	public void increment() {
		state.ic++;
		trace.info("ic<-"+state.ic);
	}

	/**
	 * Decrement the instruction counter.
	 */
	public void decrement() {
		 state.ic--;
		 trace.info("ic<-"+state.ic);
	}
	
	/**
	 * String representation of the the current state.
	 */
	public String toString() {
		return state.toString();
		
	}
	
	/**
	 * Returns state without labels.
	 * @return
	 */
	public String getState() {
		return state.getState();
	}
	
	public int getPtr() {
		return state.ptr;
	}

	public void setPtr(int ptr) {
		state.ptr = ptr;
	}
	
	/**
	 * Returns a memory dump as a String
	 * @return
	 */
	public String dumpMemory() {
		return mmu.toString();
	}

	/**
	 * Write a block of data to the given frame. 
	 * @param frame
	 * @param data
	 * @throws HardwareInterruptException
	 */
	public void writeFrame(int frame, String data) throws HardwareInterruptException {
		trace.finer("-->");
		mmu.writeFrame(state.ptr,frame, data);
		trace.info(frame+"<-"+data);
		trace.finer("<--");
	}
	
	/**
	 * Write a block of data to the given logical addr 
	 * @param logicalAddr
	 * @param data
	 * @throws HardwareInterruptException
	 */
	public void writePage(int logicalAddr, String data)
			throws HardwareInterruptException {
		trace.finer("-->");
		mmu.write(state.ptr,logicalAddr,data);
		trace.info(logicalAddr + "<-" + data);
		trace.finer("<--");
	}

	/**
	 * Clear the memory array
	 */
	public void clearMemory() {
		mmu.clear();
	}

	/**
	 * Read the block from the given logical addr. 
	 * @param logicalAddr
	 * @return The data
	 * @throws HardwareInterruptException
	 */
	public String readBlock(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine("Reading from " + logicalAddr);
		trace.finer("<--");
		return mmu.read(state.ptr,logicalAddr);
	}

	/**
	 * Allocated the given page.
	 * @param pageNumber
	 * @return
	 */
	public int allocatePage(int pageNumber) {
		return mmu.allocatePage(pageNumber);
	}

	public int getPtl() {
		return state.ptl;
	}

	public void setPtl(int ptl) {
		state.ptl = ptl;
	}
	
	public int incrementPtl() {
		return state.ptl++;
	}

	/**
	 * Initialized the page table
	 */
	public void initPageTable() {
		setPtr(mmu.initPageTable());
		setPtl(0);
		trace.info("ptr="+getPtr()+", ptl="+getPtl());

	}

	/**
	 * Free the allocated pages and page table itself.
	 */
	public void freePageTable() {
		mmu.freePageTable();
		
	}

//	/**
//	 * Clears all interrupts.
//	 */
//	public void clearInterrupts() {
//		setSi(Interrupt.CLEAR);
//		setTi(Interrupt.CLEAR);
//		setPi(Interrupt.CLEAR);
//		setIOi(Interrupt.CLEAR);
//	}
	public MMU getMMU() {
		return mmu;
	}
	/**
	 * Checks if the page fault is valid based on the IR
	 * @return
	 */
	public boolean validatePageFault() {

		if (state.ir == null 
				|| state.ir.startsWith(CPU.GET)
				|| state.ir.startsWith(CPU.STORE)) {
			trace.info("valid page fault on IR="+state.ir);
			return true;
		}
		else {
			trace.severe("invalid page fault on IR="+state.ir);
			return false;
		}
	}
	
	/**
	 * Restore the CPUState based on the given instance
	 * @param cpu
	 */
	public void setState(CPUState state) {
		this.state = state;
	}
	/**
	 * 
	 * @return
	 */
	public CPUState getCPUState() {
		return state;
	}
}
