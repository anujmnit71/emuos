/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

//import javax.net.ssl.SSLEngineResult.Status;

import emu.hw.CPU;
import emu.hw.CPU.Interrupt;
import emu.hw.MMU;
import emu.util.TraceFormatter;

/**
 * Kernel for EmuOS
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 * 
 */

public class Kernel {
	/**	
	 * For tracing
	 */
	static Logger trace;
	private static Kernel ref;
	/**
	 * CPU instance
	 */
	CPU cpu;
	/* 
	 * Memory
	 */
	MMU mmu;
	/**
	 * The current process (or job)
	 */
	Process p;
	/**
	 * The buffered reader for reading the input data
	 */
	BufferedReader br;
	/**
	 * The writer for writing the output file.
	 */
	BufferedWriter wr;
	/**
	 * number of processes executed
	 */
	int processCount;
	/**
	 * Buffers the program output
	 */
	ArrayList<String> outputBuffer;
	/**
	 * Count of total cycles
	 */
	private int cycleCount;
	
	/**
	 * Buffers the last line read from the input stream
	 */
	private String lastLineRead;
	
	/**
	 * Check if the last line has been used yet.
	 */
	boolean lineBuffered = false;
	
	boolean EndOfProcessing = false;
	
	/**
	 * Control Flags for interrupt handling
	 * CONTINUE current processing and return to slaveMode
	 * ABORT the current process and continue processing job cards
	 * TERMINATE the OS
	 * INTERRUPT iterate loop again
	 */
	private enum KernelStatus {
		CONTINUE,
		ABORT,
		TERMINATE, 
		INTERRUPT
	}
	
	/**
	 * table containing error messages
	 */
	ErrorMessages errMsg;
	
	public enum ErrorMessages {
		UNKNOWN    (-1,"Unknown Error"),
		NOERR  ( 0,"No Error"),
		OUTOFDATA ( 1,"Out of Data"),
		LINELIMITEXCEEDED   ( 2,"Line Limit Exceeded"),
		TIMELIMITEXCEEDED ( 3,"Time Limit Exceeded"),
		OPCODEERROR  ( 4,"Operation Code Error"),
		OPERANDFAULT  ( 5,"Operand Fault"),
		INVALIDPAGEFAULT   ( 6,"Invalid Page Fault");
		int errorCode;
		String message;
		ErrorMessages (int errorCode, String message){
			this.errorCode = errorCode;
			this.message = message;
		}
		
		public int getErrCode(){
			return errorCode;
		}
		
		public String getMessage(){
			return message;
		}
		public static ErrorMessages set(int err) {
			for (ErrorMessages m: values()) {
				if (m.getErrCode() == err) return m;
			}
			return UNKNOWN;
		}
		
	}
	
	/**
	 * Starts the OS
	 * @param args 
	 * 		args[0] Input File
	 * 		args[1] Output File
	 * 		args[2] Trace Level
	 * 		args[3] Trace file
	 */
	public static void main(String[] args) {
		
		initTrace(args);
		
		if (args.length < 2) {
			trace.severe("I/O files missing.");
			System.exit(1);
		}
		
		String inputFile = args[0];
		String outputFile = args[1];
		
		Kernel emu = null;
		
		try {
			emu = Kernel.init(inputFile, outputFile);
		} catch (IOException ioe) {
			trace.log(Level.SEVERE, "IOException", ioe);
		} catch (Exception e){
			trace.log(Level.SEVERE, "Exception", e);
		}
		emu.run();


	}
	private void run() {
		do {
		  try {
			load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		} while (EndOfProcessing = false);
	}
	
	/**
	 * Initialize the trace
	 * @param args
	 */
	private static void initTrace(String[] args) {
		try {
			
			//Create Logger
			trace = Logger.getLogger("emuos");
			
			//Determine log level
			Level l = Level.INFO;
			if (args.length > 2) {
				l = Level.parse(args[2]);	
			}
			
			trace.setLevel(l);
			
			//TODO can't seem to get the same formatting/levels into the eclipse console handler
			//ConsoleHandler ch = new ConsoleHandler();
			//ch.setFormatter(new TraceFormatter());
			//trace.addHandler(ch);
			//ch.setLevel(l);

			//Determine log file
			String logFile = "emuos.log";
			if(args.length > 3) {
				logFile = args[3];
			}
			
			// Create an appending file handler
		    FileHandler handler = new FileHandler(logFile);
		    handler.setFormatter(new TraceFormatter());
		    trace.addHandler(handler);
		    
		} catch (IOException e) {
			e.printStackTrace();
		}


	}
	/**
	 * Private Constructor
	 * @param inputFile
	 * @param outputFile
	 * @throws IOException
	 */
	private Kernel(String inputFile, String outputFile) throws IOException {
		
		trace.info("input:"+inputFile);
		trace.info("output:"+outputFile);
		//Init HW
		cpu = CPU.getInstance();
		mmu = new MMU();
		processCount = 0;

		//Init I/O
		br = new BufferedReader(new FileReader(inputFile));
		wr = new BufferedWriter(new FileWriter(outputFile));

	}
	
	/**
	 * Returns the Kernel instance.
	 * @return
	 */
	public static Kernel getInstance() {
		return ref;
	}
	
	/**
	 * Initialized the Kernel instance
	 * @param inputFile
	 * @param outputFile
	 * @return
	 * @throws IOException
	 */
	public static Kernel init(String inputFile, String outputFile) throws IOException {
		ref = new Kernel(inputFile, outputFile);
		return ref;
	}
	

	
	public void abort(ErrorMessages error) {
		trace.finer("-->");
		setError(error);
		trace.fine("Aborting program");
		terminate();
	}
	
	/**
	 * Loads the program into memory and starts execution.
	 * @throws IOException 
	 */
	public KernelStatus load() throws IOException {
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		
		String nextLine = br.readLine();

		while (nextLine != null) {
			//Check for EOJ
			if (nextLine.startsWith(Process.JOB_END)) {
									
				finishProcess();
				
				trace.fine("Finished job "+p.getId());
				trace.info("Memory Dump of "+p.getId()+":"+cpu.dumpMemory());
				
				//read next line
				nextLine = br.readLine();
				trace.fine(nextLine);
			}
			
			if (nextLine == null || nextLine.isEmpty()) {
				trace.fine("skipping empty line...");
				nextLine = br.readLine();
				//exit();
				continue;
			}
			else if (nextLine.startsWith(Process.JOB_START)) {
				trace.info("Loading job:"+nextLine);

				checkForCurrentProcess();

				//Allocate the page table
				cpu.initPageTable();
				
				//Parse Job Data
				String id = nextLine.substring(4, 8);
				int maxTime = Integer.parseInt(nextLine.substring(8, 12));
				int maxPrints = Integer.parseInt(nextLine.substring(12, 16));
				
				//Reads first program line
				String programLine = br.readLine();
				int pagenum = 0;
				int framenum = 0;
				
				//Write each block of program lines into memory
				while (programLine != null) {
					
					if (programLine.equals(Process.JOB_END) 
							|| programLine.equals(Process.JOB_START)) {
						trace.info("breaking on "+programLine);
						break;
					}
					else if (programLine.equals(Process.DATA_START)) {
						//trace.info("start cycle "+incrementCycleCount());
						trace.info("data start on "+programLine);
						trace.fine("Memory contents: " + cpu.dumpMemory());
						trace.fine("CPU: "+cpu.toString());
						
						p = new Process(id, maxTime, maxPrints, br, wr);
						p.startExecution();
						processCount++;
						trace.finer("<-- DATA_START");
						return retval;
					}
					else {
						trace.info("start cycle "+incrementCycleCount());
						framenum = cpu.allocatePage(pagenum);
						cpu.writeFrame(framenum, programLine);
					}
					pagenum+=1;
					programLine = br.readLine();
				}
			}
			else {
				trace.warning("skipped data line:"+nextLine);
				nextLine = br.readLine();
			}
		}

		trace.info("No more jobs, exiting");
		EndOfProcessing = true;
		checkForCurrentProcess();

		trace.finer("<--");
		return retval;
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	private void checkForCurrentProcess() throws IOException {
		//Check for current process
		if (p != null && p.isRunning()) {
			trace.warning("Process "+p.getId()+" never finished");
			setError(ErrorMessages.UNKNOWN);
			finishProcess();
		}
		
	}

	/**
	 * Processing of a read from the GD instruction
	 * @return
	 */
	public void read() {
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		trace.fine("Entering KernelStatus Read, who reads a line");
		
		// get memory location and set interrupt if one exists
		int irValue = cpu.getOperand();
		cpu.setPi(irValue);

		// read next data card
		if (!lineBuffered) {
				try {
					lastLineRead = br.readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			trace.fine("data line from input file: "+lastLineRead);			
			lineBuffered = true;
		}
		else {
			trace.fine("using buffered line: "+lastLineRead);
		}
		
		trace.info("operand:"+irValue+" pi="+cpu.getPi().getValue());
		// If next data card is $END, TERMINATE(1)
		if (lastLineRead.startsWith(Process.JOB_END)){
			abort(ErrorMessages.OUTOFDATA);
		// Increment the time counter for the GD instruction
		} else if (!p.incrementTimeCountMaster()){
			abort(ErrorMessages.TIMELIMITEXCEEDED);
		} else {
			// write data from data card to memory location
			if (cpu.getPi() == Interrupt.CLEAR) {				
					mmu.write(irValue, lastLineRead);
					lineBuffered = false;
				cpu.setSi(Interrupt.CLEAR);
			} else
				retval = KernelStatus.INTERRUPT;
		}
		trace.finer(retval+"<--");
	}
	
	/**
	 * Processing of a write from the PD instruction
	 * @return
	 */
	public void write(){
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		int irValue = 0;
		// Increment the line limit counter
		if (!p.incrementPrintCount()) {
			retval = KernelStatus.INTERRUPT;
		// Increment the time counter for the PD instruction
		} else if (!p.incrementTimeCountMaster()){
			setError(ErrorMessages.TIMELIMITEXCEEDED);
			retval = KernelStatus.ABORT;
		} else {
			// get memory location and set exception if one exists
			irValue = cpu.getOperand();
			cpu.setPi(irValue);
			if (cpu.getPi() == Interrupt.CLEAR) {
				// write data from memory to the process outputBuffer
					p.write(cpu.readBlock(irValue));
				cpu.setSi(Interrupt.CLEAR);
			} else {
				retval = KernelStatus.INTERRUPT;
			}			
		}
		trace.finer(retval+"<--");
	}
	
	/**
	 * Called on program termination.
	 * @throws IOException
	 */
	public void terminate() {

		trace.finer("-->");
		
		KernelStatus retval = KernelStatus.CONTINUE;
		
		//Free the page table
		cpu.freePageTable();
		
		//Toss the line that might've been read
		lineBuffered=false;
		
		//Clear all interrupts
		cpu.clearInterrupts();

		//Write 2 empty lines to the output
		try {
			wr.write("\n\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Load the next user program
		try {
			retval = load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		trace.finer(retval+"<--");
	}
	
	/**
	 * Halts the OS
	 */
	public void exit() {
		System.exit(0);
	}
	
	/**
	 * Returns the CPU
	 * @return
	 */
	public CPU getCpu() {
		return cpu;
	}

	/**
	 * Master mode 
	 */
	public void masterMode() {
		trace.finer("-->");
		trace.info("Entering master mode for cycle "+cycleCount);
		
		switch (cpu.getTi()) {
		
		case CLEAR:
			/*
			 * Handle Supervisor Interrupt
			 * TI SI
			 * -- --
			 * 0  1  READ
			 * 0  2  WRITE
			 * 0  3  TERMINATE(0)
			 */
			switch (cpu.getSi()) {
			case READ:
				trace.finest("SI interrupt read");
				read();
				break;
			case WRITE:
				trace.finest("SI interrupt write");
				write();
				break;
			case TERMINATE:
				//	Dump memory
				trace.finest("SI interrupt terminate");
				terminate();
				break;
			}
			
			/*
			 * Handle Program Interrupt
			 * TI PI
			 * -- --
			 * 0  1  TERMINATE(4)
			 * 0  2  TERMINATE(5)
			 * 0  3  If Page Fault, ALLOCATE, update page table, Adjust IC if necessary
			 *       EXECUTE USER PROGRAM OTHERWISE TERMINTAE(6)
			 */
			switch (cpu.getPi()) {
			case OPERAND_ERROR:
				abort(ErrorMessages.OPERANDFAULT);
				break;
			case OPERATION_ERROR:
				abort(ErrorMessages.OPCODEERROR);
				break;
			case PAGE_FAULT:
				boolean valid = mmu.validatePageFault(cpu.getIr());
				if (valid){
					int frame = cpu.allocatePage(cpu.getOperand() / 10); 
					trace.fine("frame "+frame+" allocated for page "+cpu.getOperand());
					cpu.decrement();
				}	
				else {
					abort(ErrorMessages.INVALIDPAGEFAULT);
				}
				break;
			}
			break;
		case TIME_ERROR:
			/*
			 * Handle Supervisor Interrupt
			 * TI SI
			 * -- --
			 * 2  1  TERMINATE(3)
			 * 2  2  WRITE,THEN TERMINATE(3)
			 * 2  3  TERMINATE(0)
			 */	
			switch (cpu.getSi()) {
			case READ:
				abort(ErrorMessages.TIMELIMITEXCEEDED);
				break;
			case WRITE:
				write();
				abort(ErrorMessages.TIMELIMITEXCEEDED);
				break;
			case TERMINATE:
				terminate();
				break;
			}
			
			/*
			 * Handle Program Interrupt
			 * TI PI
			 * -- --
			 * 2  1  TERMINATE(3,4)
			 * 2  2  TERMINATE(3,5)
			 * 2  3  TERMINATE(3)
			 */
			switch (cpu.getPi()) {
			case OPERAND_ERROR:
				setError(ErrorMessages.TIMELIMITEXCEEDED);
				abort(ErrorMessages.OPERANDFAULT);
				break;
			case OPERATION_ERROR:
				setError(ErrorMessages.TIMELIMITEXCEEDED);
				abort(ErrorMessages.OPCODEERROR);
				break;
			case PAGE_FAULT:
				abort(ErrorMessages.TIMELIMITEXCEEDED);
				break;
			default:
				setError(ErrorMessages.TIMELIMITEXCEEDED);
				abort(ErrorMessages.TIMELIMITEXCEEDED);
				break;
			}
			break;
		}
		
		/*
		 * Abort for IO Interrupt
		 */
//		if (cpu.getIOi().equals(Interrupt.IO)){
//			status = KernelStatus.ABORT;
//			cpu.setIOi(Interrupt.CLEAR);
//		}
		
		/*
		 * This is handles a programming error i.e. bugs in setting Interrupts
		 */
		if (cpu.getPi().equals(Interrupt.WRONGTYPE)
				|| cpu.getTi().equals(Interrupt.WRONGTYPE)
				|| cpu.getSi().equals(Interrupt.WRONGTYPE)){
			exit();
		}
		trace.fine("Status: "+cpu.dumpInterrupts());
		

		
		
		
		
		incrementCycleCount();
		trace.finer("<--");
	}
	
	/**
	 * Slave mode
	 */
	public void slaveMode() {
		trace.info("Entering slave mode for cycle "+cycleCount);
		cpu.fetch();
		cpu.increment();
		cpu.execute();
	}
	
	/**
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public void finishProcess() throws IOException {
		trace.finer("-->");
		wr.write(p.getId()+" "+p.getTerminationStatus()+"\n");
		wr.write(cpu.getState());
		wr.write("    "+p.getTime()+"    "+p.getLines());
		wr.newLine();
		wr.newLine();
		wr.newLine();
		
		ArrayList<String> buf = p.getOutputBuffer();
		for (String line : buf) {
			 wr.write(line);
			 wr.newLine();
		}
		wr.flush();
		p.terminate();
		trace.finer("<--");
	}
	
	/**
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public String toString(){
		return "cpu cycles "+cpu.getClock()+"   total processes "+processCount; 
	}

	/**
	 * Check if there already exists and error in the current process. 
	 * If one does not exist set new status effectively clearing existing status and setting 
	 * errorInProcess to true
	 * If one exists append new error to existing status otherwise clear 
	 * 
	 * @param err
	 */
	public void setError(ErrorMessages errMsg) {
		trace.finer("-->");
		trace.fine("setting err="+errMsg.getErrCode()+"; "+errMsg.getMessage());
		if (!p.getErrorInProcess()){
			p.setErrorInProcess();
			p.setTerminationStatus(errMsg.getMessage());	
		} else {
			p.appendTerminationStatus(errMsg.getMessage());	
		}
		trace.info("termination status="+p.getTerminationStatus());
		trace.finer("<--");
	}
	
	/**
	 * Increments the master/slave cycle count
	 */
	private int incrementCycleCount() {
		return cycleCount++;
	}
}
