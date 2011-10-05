/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 * Hardware is made up of the cpu, ram and a memory management unit
 */
package emu.hw;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 */
public class CPU {
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
	 * Memory Management Unit
	 */
	private MMU mmu;
	
	/**
	 * All interrupts are grouped together. Their types are verified when set.
	 * Represented values are based on phase 2 requirements
	 * retval is what a function will return if an interrupt is thrown
	 * type is the type of interrupt 
	 * errorCode is the error message to which the interrupt corresponds 
	 */
	public enum Interrupt {
		CLEAR           ( 0,515, InterruptType.MASTER    , -1),
		WRONGTYPE       (-1,525, InterruptType.MASTER    , -1),
	    READ            ( 1,516, InterruptType.SUPERVISOR, -1),
	    WRITE           ( 2,517, InterruptType.SUPERVISOR, -1),
	    TERMINATE       ( 3,518, InterruptType.SUPERVISOR, -1),
		UNKNOWN         (-1,519, InterruptType.DEFAULT   , -1),
		IO              ( 1,520, InterruptType.IO        , -1),
		TIME_ERROR      ( 2,521, InterruptType.TIME      , 3),
		OPERATION_ERROR ( 1,522, InterruptType.PROGRAM   , 4),
		OPERAND_ERROR   ( 2,523, InterruptType.PROGRAM   , 5),
		PAGE_FAULT      ( 3,524, InterruptType.PROGRAM   , 6);
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
		
	}
	
	/**
	 * Initialize CPU. Clear all interrupts and configure the memory management unit
	 */
	private CPU() {
		clearInterrupts();
		trace.info(dumpInterupts());
		mmu = new MMU(300,4,10);
	}
	
	/**
	 * @return state of all interrupts
	 */
	public String dumpInterupts() {

		return "si="+getSi().getValue()+" " +
				"pi="+getPi().getValue()+" " +
				"ti="+getTi().getValue()+" " +
				"ioi="+getIOi().getValue();
	}

	/**
	 * @return	instance of the cpu
	 */
	public static CPU getInstance() {

		if (ref == null) {
			ref = new CPU();
		}

		return ref;
		
	}
	/**
	 * All the valid interrupt types
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
	 * @return	instruction in IR register
	 */
	public String getIr() {
		return ir;
	}

	/**
	 * set instruction register
	 * @param ir	program instruction
	 */
	public void setIr(String ir) {
		this.ir = ir;
	}

	/**
	 * get toggle
	 * @return	state of toggle
	 */
	public boolean isC() {
		return c;
	}
	
	/**
	 * Get C as T or F
	 * @return	boolean	T if true, F if false
	 */
	public String getCString() {
		return Boolean.toString(c).substring(0,1).toUpperCase();
	}

	/**
	 * set toggle
	 * @param c	value to set toggle
	 */
	public void setC(boolean c) {
		this.c = c;
	}
	/** 
	 * get the current instruction count
	 * @return	instruction count in IC register
	 */
	public int getIc() {
		return ic;
	}

	/**
	 * set the instruction count
	 * @param ic	value of the instruction counter 
	 */
	public void setIc(int ic) {
		this.ic = ic;
	}
	/**
	 * get the cpu clock
	 * @return	current cpu clock
	 */
	public int getClock() {
		return clock;
	}

	/**
	 * get Interupt type
	 * @return	supervisor interrupt
	 */
	public Interrupt getSi() {
		return si;
	}

	/**
	 * set Interrupt type
	 * @param si	value of supervisor interrupt
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
	 * @param value	integer which will be converted to supervisor interrupt
	 */
	public void setSi(int value) {
		Interrupt i = Interrupt.set(value);
		setSi(i);
	}
	
	/**
	 * get ProgramInterrupt type
	 * @return pi	value of program interrupt
	 */
	public Interrupt getPi() {
		return pi;
	}

	/**
	 * set ProgramInterrupt type
	 * @param pi	value of program interrupt
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
	 * @param value	integer which will be converted to program interrupt
	 */
	public void setPi(int value) {
		Interrupt i = Interrupt.set(value);
		setPi(i);
	}
	
	/**
	 * get Interrupt type
	 * @return ti	value of time interrupt
	 */
	public Interrupt getTi() {
		return ti;
	}

	/**
	 * set time interrupt
	 * @param ti	value of time interrupt	
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
	 * @param value	integer which will be converted to time interrupt
	 */
	public void setTi(int value) {
		Interrupt i = Interrupt.set(value);
		setTi(i);
	}
	
	/**
	 * get Interrupt type
	 * @return ioi	value of I/O interrupt
	 */
	public Interrupt getIOi() {
		return ioi;
	}

	/**
	 * set time interrupt type
	 * @param ioi	value of I/O interrupt
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
	 * set IO interrupt based on integer value
	 * @param value	integer which will be converted to I/O interrupt
	 */
	public void setIOi(int value) {
		Interrupt i = Interrupt.set(value);
		setIOi(i);
	}
	
	/**
	 * get operand from instruction in instruction register
	 * @return retval	operand from instruction
	 */
		public int getOperand() {
			
			int retval = -1; 
			try {
				retval = Integer.parseInt(ir.substring(2,4));
				trace.fine("operand: "+retval);
			} catch (NumberFormatException e) {
				trace.severe("invalid operand format:"+ir.substring(2,4));
				retval = Interrupt.OPERAND_ERROR.getRetval();
			}
				
			if (retval < 0 || retval > 100){
				retval = Interrupt.OPERAND_ERROR.getRetval();
			}
			return retval;
		}
	
	/**
	 * Execute an instruction
	 * \dot digraph cpu_execute {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * execute0 [label="logical address = operand"];
	 * execute2 [label="set program interrupt ... operand error"];
	 * execute4 [label="load ( logical address )"];
	 * execute6 [label="set program interrupt ... operation error"];
	 * execute8 [label="set supervisor interrupt ... write"];
	 * execute10 [label="set supervisor interrupt ... read"];
	 * execute12 [label="set supervisor interrupt ... terminate"];
	 * execute14 [label="get operand"];
	 * execute16 [label="set IC to logical address"];
	 * execute18 [label="set program interrupt ... operand error"];
     * execute20 [label="get operand"];
 	 * execute22 [label="set program interrupt ... operand error"];
 	 * execute24 [label="store ( logical address, contents of GR )"];
 	 * execute26 [label="set program interrupt ... operand error"];
 	 * execute28 [label="get operand"];
 	 * execute30 [label="set toggle to true"];
 	 * execute32 [label="set toggle to false"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * execute1 [label="if IR contains LR"];
	 * execute1a [label="if logical address is valid"];
	 * execute3 [label="else if IR contains SR"];
	 * execute3a [label="if GR is empty"];
	 * execute3b [label="if logical address is valid"];
	 * execute5 [label="else if IR contains CR"];
	 * execute5a [label="if GR is empty"];
	 * execute5b [label="if logical address is valid"];
	 * execute5c [label="if GR equals contents of logical address"];
	 * execute9 [label="else if IR contains BT"];
	 * execute9a [label="if C is set"];
	 * execute9b [label="if logical address is valid"];
	 * execute11 [label="else if IR contains GD"];
	 * execute13 [label="else if IR contains PD"];
	 * execute15 [label="else if IR contains H"];
	 * execute17 [label="if an interrupt has been set"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * execute_begin [label="begin", color="chartreuse"];
	 * execute_exception [label="hardware interrupt", color="firebrick1"];
	 * execute_return [label="return", color="firebrick1"];
	 * 
	 * {rank=same; execute3 execute2 execute4;};
	 * {rank=same; execute5 execute24 execute22;};
	 * {rank=same; execute9 execute26 execute30 execute32;};
	 * {rank=same; execute11 execute16 execute18;};
	 * 
	 * execute_begin -> execute1;
	 * execute1 -> execute3 -> execute5 -> execute9 -> execute11 -> execute13 -> execute15 [label="false"];
	 * execute1 -> execute0 [label="true"];
	 * execute0 -> execute1a;
	 * execute1a -> execute4 [label="true"];
	 * execute4 -> execute17;
	 * execute2 -> execute17;
	 * execute1a -> execute2 [label="false"];
	 * 
	 * execute3 -> execute3a -> execute22 [label="true"];
	 * execute22 -> execute17;
	 * execute3a -> execute20 [label="false"];
	 * execute20 -> execute3b; 
	 * execute3b -> execute24 [label="true"];
	 * execute3b -> execute22 [label="false"];
	 * execute24 -> execute17;
	 * 
	 * execute5 -> execute5a -> execute26 [label="true"];
	 * execute26 -> execute17;
	 * execute5a -> execute28 [label="false"];
	 * execute28 -> execute5b;
	 * execute5b -> execute26 [label="false"];
	 * execute5b -> execute5c [label="true"];
	 * execute5c -> execute30 [label="true"];
	 * execute5c -> execute32 [label="false"];
	 * execute30 -> execute17;
	 * execute32 -> execute17;
	 *  
	 * execute9 -> execute9a [label="true"];
	 * execute9a -> execute14 [label="true"];
	 * execute9a -> execute17 [label="false"];
	 * execute14 -> execute9b;
	 * execute9b -> execute16 [label="true"];
	 * execute9b -> execute18 [label="false"];
	 * execute16 -> execute17;
	 * execute18 -> execute17;
	 * 
	 * execute11 -> execute10 [label="true"];
	 * execute10 -> execute17;
	 * 
	 * execute13 -> execute8 [label="true"];
	 * execute8 -> execute17;
	 * 
	 * execute15 -> execute6 [label="false"];
	 * execute15 -> execute12 [label="true"];
	 * execute12 -> execute17;
	 * execute6 -> execute17;
	 * execute17 -> execute_return [label="false"];
	 * execute17 -> execute_exception [label="throw exception"];
	 * } \enddot
	 * @throws HardwareInterruptException	when any type of interrupt is set
	 */
	public void execute() throws HardwareInterruptException {
		trace.finer("-->");
		trace.info(toString());
		clock++;
		int logicalAddr = 0;
		if (ir.startsWith(LOAD)) {
			logicalAddr = getOperand();
			pi = Interrupt.set(logicalAddr);
			if (pi == Interrupt.CLEAR) {
				gr = mmu.load(logicalAddr);
				trace.info("r<-"+gr);
			}
		}else if (ir.startsWith(STORE)) {
			if (gr == null)
				pi = Interrupt.OPERAND_ERROR;
			else {
				logicalAddr = getOperand();
				pi = Interrupt.set(logicalAddr);
					if (pi == Interrupt.CLEAR)
						mmu.store(logicalAddr,gr);
			}
		}else if (ir.startsWith(COMPARE)) {
			if (gr == null)
				pi = Interrupt.OPERAND_ERROR;
			else {
				logicalAddr = getOperand();
				pi = Interrupt.set(logicalAddr);
				if (pi == Interrupt.CLEAR) {
					c = mmu.load(logicalAddr).equals(gr);
					trace.info("c<-"+c);
				}
			}
		}else if (ir.startsWith(BRANCH)) {
			if (c) {
				logicalAddr = getOperand();
				pi = Interrupt.set(logicalAddr);
					if (pi == Interrupt.CLEAR)
						ic = logicalAddr;
			}			
		}else if (ir.startsWith(GET)) {
			si = Interrupt.READ;
			//trace.info("si<-"+Interrupt.READ.getValue());
		}else if (ir.startsWith(PUT)) {
			si = Interrupt.WRITE;
			//trace.info("<-"+Interrupt.WRITE.getValue());
		}else if (ir.startsWith(HALT)) {
			si = Interrupt.TERMINATE;	
			//trace.info("si<-"+Interrupt.TERMINATE.getValue());
		}else {
			pi = Interrupt.OPERATION_ERROR;
		}
		
		/*
		 * wait until all instructions have been handled before throwing
		 * exception
		 */
		if (ti != Interrupt.CLEAR
				|| si != Interrupt.CLEAR
				|| pi != Interrupt.CLEAR){
			trace.finer("<--");
			throw new HardwareInterruptException();
		}
		trace.finer("<--");
	}
	
	/**
	 * Load an instruction into IR 
	 * @throws HardwareInterruptException	when page fault interrupt is thrown 
	 */
	public void fetch() throws HardwareInterruptException {
		ir = mmu.load(ic);
		trace.info(ir+" from logical address "+ic);
	}
	
	/**
	 * Increment the instruction counter.
	 */
	public void increment() {
		 ic++;
		 trace.info("ic<-"+ic);
	}

	/**
	 * Decrement the instruction counter.
	 */
	public void decrement() {
		 ic--;
		 trace.info("ic<-"+ic);
	}
	
	/**
	 * String representation of the the current state.
	 */
	public String toString() {
		return "ic="+ic+" ir="+ir+" gr="+gr+" c="+getCString()+" "+dumpInterupts();
		
	}
	
	/**
	 * Returns state without labels.
	 * @return	current state of cpu
	 */
	public String getState() {
		return ic+"    "+ir+"    "+gr+"    "+getCString();
	}
	
	/**
	 * @return	page table register
	 */
	public int getPtr() {
		return ptr;
	}

	/**
	 * @param ptr	set the page location of the page table register
	 */
	public void setPtr(int ptr) {
		this.ptr = ptr;
	}
	
	/**
	 * Returns a memory dump as a String
	 * @return	representation of memory
	 */
	public String dumpMemory() {
		return mmu.toString();
	}

	/**
	 * Checks if the page fault is valid based on the IR
	 * @return	true if the page fault is valid otherwise false
	 */
	public boolean validatePageFault() {

		if (ir == null 
				|| ir.startsWith(CPU.GET)
				|| ir.startsWith(CPU.STORE)) {
			trace.info("valid page fault on IR="+ir);
			return true;
		}
		else {
			trace.severe("invalid page fault on IR="+ir);
			return false;
		}
	}

	/**
	 * Write a block of data to the given frame. 
	 * @param frame	
	 * @param data
	 */
	public void writeFrame(int frame, String data) {
		trace.finer("-->");
		mmu.writeFrame(frame, data);
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
		mmu.write(logicalAddr,data);
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
	 * @return 	data read from memory
	 * @throws HardwareInterruptException
	 */
	public String readBlock(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine("Reading from " + logicalAddr);
		trace.finer("<--");
		return mmu.read(logicalAddr);
	}

	/**
	 * Allocated the given page.
	 * @param pageNumber
	 * @return	frame number
	 */
	public int allocatePage(int pageNumber) {
		return mmu.allocatePage(pageNumber);
	}

	/**
	 * @return page table length
	 */
	public int getPtl() {
		return ptl;
	}

	/**
	 * @param ptl page table length
	 */
	public void setPtl(int ptl) {
		this.ptl = ptl;
	}
	
	/**
	 * @return	incremented length of the page table
	 */
	public int incrementPtl() {
		return ptl++;
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

	/**
	 * Clears all interrupts.
	 */
	public void clearInterrupts() {
		setSi(Interrupt.CLEAR);
		setTi(Interrupt.CLEAR);
		setPi(Interrupt.CLEAR);
		setIOi(Interrupt.CLEAR);
	}

}
