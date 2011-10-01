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
import emu.hw.HardwareInterruptException;
//import emu.hw.MMU;
//import emu.hw.RAM;
import emu.util.TraceFormatter;

/**
 * Kernel for EmuOS
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 * 
 */

/*!
\dot
digraph kernel {
  splines=true
  rankdir=bt
  node [shape=box]; a0;
  node [shape=ellipse]; start; end;
  subgraph cluster_0 {
    node [shape=box]; b1; c1; d1; f1;
    node [shape=diamond]; a1; e1;
    a1 [label="done?"];
    b1 [label="fetch"];
    c1 [label="increment"];
    d1 [label="execute"];
    e1 [label="HardwareInterrupt?"];
    f1 [label="interruptHandler"];
    a1 -> b1 [label="false"];
    b1 -> c1 -> d1 -> e1;
    e1 -> f1 [label = "true"];
	e1 -> a1 [label = "false"];
    f1 -> a1 [label = "restart loop"];
    label="slaveMode";
  }
  start -> a0;
  a0 -> a1;
  a1 -> end [label="true"];
  a0 [label="cpu.writeBootSector()"];
}
\enddot
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
	 * Boot sector
	 */
	String bootSector = "H                                       ";
	/**
	 * Starts EmuOS
	 * @param args
	 */
	boolean inMasterMode;
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
	
	/**
	 * Control Flags for interrupt handling
	 * CONTINUE current processing and return to slaveMode
	 * ABORT the current process and continue processing job cards
	 * TERMINATE the OS
	 * INTERRUPT iterate loop again
	 */
	private enum KernelStatus {
		CONTINUE,ABORT, TERMINATE, INTERRUPT
	}
	
	/**
	 * table containing error messages
	 */
	ErrorMessages errMsg;
	
	public enum ErrorMessages {
		NA    (-1,"Unknown Error"),
		ZERO  ( 0,"No Error"),
		ONE   ( 1,"Out of Data"),
		TWO   ( 2,"Line Limit Exceeded"),
		THREE ( 3,"Time Limit Exceeded"),
		FOUR  ( 4,"Operation Code Error"),
		FIVE  ( 5,"Operand Fault"),
		SIX   ( 6,"Invalid Page Fault");
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
			return NA;
		}
		
	}
	
	public static final void main(String[] args) {
		
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
			emu.boot();
		} catch (IOException ioe) {
			trace.log(Level.SEVERE, "IOException", ioe);
		} catch (Exception e){
			trace.log(Level.SEVERE, "Exception", e);
		}

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
		//mmu = new MMU(300,4,10);
		processCount = 0;
		inMasterMode = true;

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
	
	/**
	 * Starts the OS by loading the HALT instruction into memory
	 * then calls to slaveMode to execute  
	 * @throws IOException
	 */

	public void boot() throws IOException {
		trace.finer("-->");
		try {
			//trace.info("starting boot process");
			trace.info("start cycle "+incrementCycleCount());
			cpu.initPageTable();
			cpu.allocatePage(0);
			cpu.writePage(0, bootSector);
			masterMode();
		} catch (HardwareInterruptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			br.close();
			wr.close();
			//Dump memory
			trace.fine("\n"+cpu.dumpMemory());

			trace.fine("Memory contents: " + cpu.dumpMemory());
			//Dump Kernel stats
			trace.fine("\n"+toString());
			//Dump memory
			trace.fine("\n"+cpu.toString());

		}
	}
	
	/**
	 * Called when control needs to be passed back to the OS
	 * though called masterMode this is more of and interrupt handler for the OS
	 * Interrupts processed in two groups TI = 0(CLEAR) and TI = 2(TIME_ERROR)
	 * @throws IOException
	 */
	public boolean interruptHandler() throws IOException {
		boolean retval = false;
		inMasterMode = true;
		KernelStatus status = KernelStatus.INTERRUPT;
		
		/*
		 * This is in a loop because new interrupts may be generated while processing request from
		 * slaveMode. KernelStatus is used to control flow through this loop.
		 */
		trace.finer("-->");
		trace.fine("Physical Memory:\n"+cpu.dumpMemory());
		while (status == KernelStatus.INTERRUPT) {
			//trace.info("start cycle "+incrementCycleCount());
			trace.info(""+cpu.dumpInterupts());
			trace.fine("Kernel status="+status);
			
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
					trace.finest("Si interrupt read");
					status = read();
					break;
				case WRITE:
					trace.finest("Si interrupt write");
					status = write();
					break;
				case TERMINATE:
					//	Dump memory
					trace.fine("Case:Terminate");

					trace.fine("Memory contents: " + cpu.dumpMemory());
					status = terminate();
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
					setError( Interrupt.OPERAND_ERROR.getErrorCode());
					cpu.setPi(Interrupt.CLEAR);
					status = KernelStatus.ABORT;
					break;
				case OPERATION_ERROR:
					setError( Interrupt.OPERATION_ERROR.getErrorCode());
					cpu.setPi(Interrupt.CLEAR);
					status = KernelStatus.ABORT;
					break;
				case PAGE_FAULT:
					boolean valid = cpu.validatePageFault();
					if (valid){
						int frame = cpu.allocatePage(cpu.getOperand() / 10); //TODO cleaner way to determine page #?
						trace.fine("frame "+frame+" allocated for page "+cpu.getOperand());
						cpu.setPi(Interrupt.CLEAR);
						cpu.decrement();
						status = KernelStatus.CONTINUE;
					} else {
						setError( ErrorMessages.SIX.getErrCode());
						status = KernelStatus.ABORT;
						cpu.setPi(Interrupt.CLEAR);
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
					setError( Interrupt.TIME_ERROR.getErrorCode());
					status = KernelStatus.ABORT;
					break;
				case WRITE:
					status = write();
					setError( Interrupt.TIME_ERROR.getErrorCode());
					status = KernelStatus.ABORT;
					break;
				case TERMINATE:
					//	Dump memory
					trace.finer("\n"+cpu.dumpMemory());
					status = terminate();
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
					setError(Interrupt.TIME_ERROR.getErrorCode());
					setError(Interrupt.OPERAND_ERROR.getErrorCode());
					cpu.setPi(Interrupt.CLEAR);
					status = KernelStatus.ABORT;
					break;
				case OPERATION_ERROR:
					setError(Interrupt.TIME_ERROR.getErrorCode());
					setError(Interrupt.OPERATION_ERROR.getErrorCode());
					cpu.setPi(Interrupt.CLEAR);
					status = KernelStatus.ABORT;
					break;
				case PAGE_FAULT:
					setError(Interrupt.TIME_ERROR.getErrorCode());
					status = KernelStatus.ABORT;
					break;
				}
				
				/*
				 * Still have to handle a plain old Time Interrupt
				 */
				if (cpu.getPi().equals(Interrupt.CLEAR)
					|| cpu.getPi().equals(Interrupt.CLEAR)) {
						setError(Interrupt.TIME_ERROR.getErrorCode());
						cpu.setTi(Interrupt.CLEAR);
						status = KernelStatus.ABORT;
					}
				break;
			}
			
			/*
			 * Abort for IO Interrupt
			 */
			if (cpu.getIOi().equals(Interrupt.IO)){
				status = KernelStatus.ABORT;
				cpu.setIOi(Interrupt.CLEAR);
			}
			
			/*
			 * This is handles a programming error i.e. bugs in setting Interrupts
			 */
			if (cpu.getPi().equals(Interrupt.WRONGTYPE)
					|| cpu.getTi().equals(Interrupt.WRONGTYPE)
					|| cpu.getSi().equals(Interrupt.WRONGTYPE)){
				return true;
			}
			trace.fine("Status "+cpu.dumpInterupts());
			
			/*
			 * Normally the loop will restart and eventually find terminate.
			 * No need to reiterate through loop if its going to call terminate.
			 */
			if (status == KernelStatus.ABORT) {
				status = terminate();
			}
			trace.fine("End of Interrupt Handling loop "+status);
		}

		// Tell slaveMode that there are no more programs to run
		if (status == KernelStatus.TERMINATE)
			retval = true;
		
		trace.fine(retval+"<--");
		return retval;
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
									
				writeProccess();
				
				trace.info("Finished job "+p.getId());
				
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
					try {
						framenum = cpu.allocatePage(pagenum);
						cpu.writeFrame(framenum, programLine);
					} catch (HardwareInterruptException e) {
						trace.info("HardwareInterruptException");
						retval = KernelStatus.INTERRUPT;
					}
					}
					pagenum+=1;
					programLine = br.readLine();
				}
			}
			else {
				trace.warning("Unexpected line:"+nextLine);
				nextLine = br.readLine();
			}
		}

		trace.info("No more jobs, exiting");
		retval = KernelStatus.TERMINATE;
		trace.finer("<--");
		return retval;
	}
	
	/**
	 * Processing of a read from the GD instruction
	 * @return
	 * @throws IOException
	 */
	public KernelStatus read() throws IOException{
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		trace.fine("Entering KernelStatus Read, who reads a line");
		
		// get memory location and set interrupt if one exists
		int irValue = cpu.getOperand();
		cpu.setPi(irValue);

		// read next data card
		if (!lineBuffered) {
			lastLineRead = br.readLine();
			trace.fine("line read from input: "+lastLineRead);			
			lineBuffered = true;
		}
		else {
			trace.fine("using buffered line: "+lastLineRead);
		}
		
		trace.info("operand:"+irValue+" pi="+cpu.getPi().getValue());
		// If next data card is $END, TERMINATE(1)
		if (lastLineRead.startsWith(Process.JOB_END)){
			setError(1);
			writeProccess();
			retval = KernelStatus.ABORT;
		// Increment the time counter for the GD instruction
		} else if (!p.incrementTimeCountMaster()){
			setError(3);
			retval = KernelStatus.ABORT;
		} else {
			// write data from data card to memory location
			if (cpu.getPi() == Interrupt.CLEAR) {				
				try {
					cpu.writePage(irValue, lastLineRead);
					lineBuffered = false;
				} catch (HardwareInterruptException e) {
					trace.info("HW interrupt:"+cpu.dumpInterupts());
					retval = KernelStatus.INTERRUPT;
				}
				cpu.setSi(Interrupt.CLEAR);
			} else
				retval = KernelStatus.INTERRUPT;
		}
		trace.finer(retval+"<--");
		return retval;
	}
	
	/**
	 * Processing of a write from the PD instruction
	 * @return
	 */
	public KernelStatus write(){
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		int irValue = 0;
		// Increment the line limit counter
		if (!p.incrementPrintCount()) {
			retval = KernelStatus.INTERRUPT;
		// Increment the time counter for the PD instruction
		} else if (!p.incrementTimeCountMaster()){
			setError(3);
			retval = KernelStatus.ABORT;
		} else {
			// get memory location and set exception if one exists
			irValue = cpu.getOperand();
			cpu.setPi(irValue);
			if (cpu.getPi() == Interrupt.CLEAR) {
				// write data from memory to the process outputBuffer
				try {
					p.write(cpu.readBlock(irValue));
				} catch (HardwareInterruptException e) {
					trace.info("HW interrupt:"+cpu.dumpInterupts());
					retval = KernelStatus.INTERRUPT;
				}
				cpu.setSi(Interrupt.CLEAR);
			} else {
				retval = KernelStatus.INTERRUPT;
			}			
		}
		trace.finer(retval+"<--");
		return retval;
	}
	
	/**
	 * Called on program termination.
	 * @throws IOException
	 */
	public KernelStatus terminate() throws IOException {

		trace.finer("-->");
		
		KernelStatus retval = KernelStatus.CONTINUE;
		
		//Free the page table
		cpu.freePageTable();
		
		//Toss the line that might've been read
		lineBuffered=false;
		
		//Clear all interrupts
		cpu.clearInterrupts();

		//Write 2 empty lines to the output
		wr.write("\n\n");
		
		// Load the next user program
		retval = load();
		
		trace.finer(retval+"<--");
		return retval;
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
	 * Master execution cycle 
	 * @throws HardwareInterruptException
	 * @throws IOException 
	 * @throws SoftwareInterruptException
	 */
	public void masterMode() throws IOException {
		trace.finer("-->");
		inMasterMode = false;
		boolean done = false;
		while (!done) {
			try {
				trace.info("start cycle "+incrementCycleCount());
				slaveMode();
			} catch (HardwareInterruptException hie) {
				trace.info("start cycle "+incrementCycleCount());
				trace.info("HW Interrupt from slave mode");
				trace.fine(cpu.dumpInterupts());
				done = interruptHandler();
				inMasterMode = false;
			}
		}
		trace.finer("<--");
	}
	
	/**
	 * Slave execution cycle
	 * @throws HardwareInterruptException
	 */
	public void slaveMode() throws HardwareInterruptException {
		trace.info("start slave mode ");
		cpu.fetch();
		cpu.increment();
		cpu.execute();
		p.incrementTimeCountSlave();
	}
	
	/**
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public void writeProccess() throws IOException {
		trace.finer("-->");
		wr.write(p.getId()+"    "+p.getTerminationStatus()+"\n");
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
	public void setError(int err) {
		trace.finer("-->");
		trace.info("setting err="+err);
		errMsg = ErrorMessages.set(err);
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
	 * 
	 */
	private int incrementCycleCount() {
		return cycleCount++;
	}
}
