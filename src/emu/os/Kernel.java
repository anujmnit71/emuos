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
 * 
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
	/**
	 * The current process (or job)
	 */
	Process p;
	/**
	 * The buffered reader for reading the input data
	 */
	BufferedReader br;
	/**
	 * The writer of the output file.
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
	 * Indicates if the OS is in master mode or slave mode
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
	 * Job cards
	 */
	public static final String JOB_START = "$AMJ";
	public static final String DATA_START = "$DTA";
	public static final String JOB_END = "$EOJ";
	
	/**
	 * Control Flags for interrupt handling CONTINUE current processing and
	 * return to slaveMode ABORT the current process and continue processing job
	 * cards TERMINATE the OS INTERRUPT iterate loop again
	 */
	private enum KernelStatus {
		CONTINUE, ABORT, TERMINATE, INTERRUPT
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

		ErrorMessages(int errorCode, String message) {
			this.errorCode = errorCode;
			this.message = message;
		}

		public int getErrCode() {
			return errorCode;
		}

		public String getMessage() {
			return message;
		}

		public static ErrorMessages set(int err) {
			for (ErrorMessages m : values()) {
				if (m.getErrCode() == err)
					return m;
			}
			return NA;
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

			// TODO can't seem to get the same formatting/levels into the
			// eclipse console handler
			// ConsoleHandler ch = new ConsoleHandler();
			// ch.setFormatter(new TraceFormatter());
			// trace.addHandler(ch);
			// ch.setLevel(l);

			// Determine log file
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
	 * Starts the OS by loading the HALT instruction into memory then calls to
	 * slaveMode to execute
	 * \dot digraph
	 * kernel_boot { 
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
     * node [shape="box",fontsize="8",fontname="Helvetica"];
     * boot_initPageTable [label="initialize page table"];
     * boot_allocatePage [label="allocate page for boot sector"];
     * boot_bootsector [label="write boot sector to allocated frame"];
     * boot_masterMode [label="masterMode()"];
     *
     * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
     * boot_begin [label="begin", color="chartreuse"];
     * boot_return [label="return", color="firebrick1"];
     * 
     * boot_begin -> boot_initPageTable -> boot_allocatePage -> boot_bootsector -> boot_masterMode -> boot_return;
     * } \enddot
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
	 * Called when control needs to be passed back to the OS though called
	 * masterMode this is more of and interrupt handler for the OS Interrupts
	 * processed in two groups TI = 0(CLEAR) and TI = 2(TIME_ERROR) 
	 * \dot digraph
	 * kernel_interruptHandler { 
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * interruptHandler_10 [label="kernelStatus == INTERRUPT"];
	 * interruptHandler_12 [label="done = true"];
	 * interruptHandler_104 [label="handle time interrupt"];
	 * interruptHandler_106 [label="kernelStatus = ABORT"];
	 *  interruptHandler_108 [label="terminate"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * interruptHandler_11 [label="while kernelStatus == INTERRUPT"];
	 * interruptHandler_13 [label="kernelStatus == TERMINATE?"];
	 * interruptHandler_100 [label="Is there a time interrupt"];
	 * interruptHandler_105 [label="Is there a IO Error?"];
	 * interruptHandler_107 [label="kernelStatus == ABORT?"];
	 * 
	 * node [shape="plaintext",color="deeppink",fontsize="8",fontname="Helvetica"]; 
	 * interruptHandler_table_pi_1
	 * [label=<
	 * <table border="0" cellborder="1" cellspacing="0" cellpadding="3">
	 * <th>
	 * <td>TI</td>
	 * <td>PI</td>
	 * <td>action</td></th>
	 * <tr>
	 * <td>clear</td>
	 * <td>operand error</td>
	 * <td>terminate(4)</td>
	 * </tr>
	 * <tr>
	 * <td>clear</td>
	 * <td>operation error</td>
	 * <td>terminate(5)</td>
	 * </tr>
	 * <tr>
	 * <td>clear</td>
	 * <td>page fault</td>
	 * <td>validatePageFault()<br/>
	 * or terminate(6)</td>
	 * </tr>
	 * </table>>
	 * ]; interruptHandler_table_pi_2 [label=<
	 * <table border="0" cellborder="1" cellspacing="0" cellpadding="3">
	 * <th>
	 * <td>TI</td>
	 * <td>PI</td>
	 * <td>action</td></th>
	 * <tr>
	 * <td>time expired</td>
	 * <td>operand error</td>
	 * <td>terminate(3)</td>
	 * </tr>
	 * <tr>
	 * <td>time expired</td>
	 * <td>operation error</td>
	 * <td>write, then terminate(3)</td>
	 * </tr>
	 * <tr>
	 * <td>time expired</td>
	 * <td>page fault</td>
	 * <td>terminate(0)</td>
	 * </tr>
	 * </table>>
	 * ]; interruptHandler_table_si_1 [label=<
	 * <table border="0" cellborder="1" cellspacing="0" cellpadding="3">
	 * <th>
	 * <td>TI</td>
	 * <td>SI</td>
	 * <td>action</td></th>
	 * <tr>
	 * <td>clear</td>
	 * <td>read</td>
	 * <td port="si_read">read()</td>
	 * </tr>
	 * <tr>
	 * <td>clear</td>
	 * <td>write</td>
	 * <td port="si_write">write()</td>
	 * </tr>
	 * <tr>
	 * <td>clear</td>
	 * <td>terminate</td>
	 * <td>terminate(0)</td>
	 * </tr>
	 * </table>>
	 * ]; interruptHandler_table_si_2 [label=<
	 * <table border="0" cellborder="1" cellspacing="0" cellpadding="3">
	 * <th>
	 * <td>TI</td>
	 * <td>SI</td>
	 * <td>action</td></th>
	 * <tr>
	 * <td>time expired</td>
	 * <td>read</td>
	 * <td>terminate(3)</td>
	 * </tr>
	 * <tr>
	 * <td>time expired</td>
	 * <td>write</td>
	 * <td port="si_write">write, then terminate(3)</td>
	 * </tr>
	 * <tr>
	 * <td>time expired</td>
	 * <td>terminate</td>
	 * <td>terminate(0)</td>
	 * </tr>
	 * </table>>
	 * ];
	 * 
	 * {rank=same; interruptHandler_table_si_1 interruptHandler_table_si_2};
	 * {rank=same; interruptHandler_table_pi_1 interruptHandler_table_pi_2};
	 * {rank=same; interruptHandler_107 interruptHandler_106}; 
	 * {rank=same; interruptHandler_13 interruptHandler_108};
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * interruptHandler_begin [label="begin", color="chartreuse"];
	 * interruptHandler_return [label="return done", color="firebrick1"];
	 * 
	 * interruptHandler_begin -> interruptHandler_10 -> interruptHandler_11 -> interruptHandler_100;
	 * interruptHandler_100 -> interruptHandler_table_si_1 [label="false"];
	 * interruptHandler_100 -> interruptHandler_table_si_2 [label="true"]; 
	 * interruptHandler_table_si_2 -> interruptHandler_table_pi_2 -> interruptHandler_105;
	 * interruptHandler_table_si_1 -> interruptHandler_table_pi_1 -> interruptHandler_104 -> interruptHandler_105; 
	 * interruptHandler_105 -> interruptHandler_106 [label="true"]; 
	 * interruptHandler_105 -> interruptHandler_107 [label="false"];
	 * interruptHandler_106 -> interruptHandler_107;
	 * interruptHandler_107 -> interruptHandler_108 [label="true"];
	 * interruptHandler_108 -> interruptHandler_11;
	 * interruptHandler_107 -> interruptHandler_11 [label="false"];
	 * interruptHandler_11 -> interruptHandler_13;
	 * interruptHandler_13 -> interruptHandler_12 [label="true"]; 
	 * interruptHandler_13 -> interruptHandler_return [label="false"];
	 * interruptHandler_12 -> interruptHandler_return; 
	 * } \enddot
	 * 
	 * @throws IOException
	 */
	public boolean interruptHandler() throws IOException {
		boolean retval = false;
		inMasterMode = true;
		KernelStatus status = KernelStatus.INTERRUPT;
		
		/*
		 * This is in a loop because new interrupts may be generated while
		 * processing request from slaveMode. KernelStatus is used to control
		 * flow through this loop.
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

		trace.fine(retval + "<--");
		return retval;
	}
	
	/**
	 * Loads the program into memory and starts execution.
	 * \dot digraph kernel_load { 
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",sytle="blueviolet",fontsize="8",fontname="Helvetica"];
	 * load_0 [label="kernelStatus = CONTINUE"];
	 * load_2 [label="initialize page table"];
	 * load_4 [label="parse $AMJ job card"];
	 * load_6 [label="kernelStatus = TERMINATE"];
	 * load_8 [label="create process"];
	 * load_10 [label="start process execution"];
	 * load_12 [label="allocate page"];
	 * load_14 [label="write program to frame"];
	 * load_16 [label="kernelStatus = INTERRUPT"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * load_1 [label="while there is input data"];
	 * load_3 [label="if input equals $EOJ"];
	 * load_5 [label="if input equals $AMJ"];
	 * load_7 [label="if input equals $DTA"];
	 * load_9 [label="if input is empty"];
	 * load_11 [label="while there is input data"];
	 * load_13 [label="if input has $EOJ or $AMJ"];
	 * load_15 [label="catch hardware interrupt"];
	 * 
	 * node [shape="parallelogram",color="brown1",sytle="filled",fontsize="8",fontname="Helvetica"];
	 * load_input_1 [label="read from input"];
	 * load_input_2 [label="write process buffer to output buffer"];
	 * load_input_3 [label="read from input buffer"];
	 * load_input_4 [label="read from input buffer"];
	 * load_input_5 [label="read first program line"];
	 * load_input_6 [label="read from input buffer"];
	 * load_input_7 [label="read from input buffer"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * load_begin [label="begin", color="chartreuse"];
	 * load_return [label="return kernelStatus", color="firebrick1"];
	 * 
	 * {rank=same; load_input_3 load_9};
	 * {rank=same; load_5 load_input_4};
	 * {rank=same; load_input_7 load_11; };
	 * {rank=same; load_12 load_8};
	 * {rank=same; load_10 load_14};
	 * 
	 * load_begin -> load_0 -> load_input_1 -> load_1;
	 * load_1 -> load_6 [label="false"];
	 * load_1 -> load_3 [label="true"];
	 * load_3 -> load_9 [label="false"];
	 * load_3 -> load_input_2 [label="true"];
	 * load_input_2 -> load_input_3 -> load_9;
	 * load_9 -> load_input_4 [label="true"];
	 * load_input_4 -> load_1;
	 * load_9 -> load_5 [label="false"];
	 * load_5 -> load_2 [label="true"];
	 * load_2 -> load_4;
	 * load_4 -> load_input_5;
	 * load_input_5 -> load_11;
	 * load_11 -> load_13 [label="true"];
	 * load_11 -> load_6 [label="false"];
	 * load_13 -> load_1 [label="true"];
	 * load_13 -> load_7 [label="false"];
	 * load_7 -> load_8 [label="true"];
	 * load_7 -> load_12 [label="false"];
	 * load_12 -> load_14 -> load_15;
	 * load_15 -> load_16 [label="true"];
	 * load_15 -> load_input_6 [label="false"];
	 * load_input_6 -> load_11; 
	 * load_5 -> load_input_7 [label="false"];
	 * load_input_7 -> load_return;
	 * load_16 -> load_return;
	 * load_8 -> load_10 -> load_return;
	 * load_6 -> load_return;
	 * } \enddot
	 * 
	 * @throws IOException
	 */
	public KernelStatus load() throws IOException {
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		
		String nextLine = br.readLine();

		while (nextLine != null) {
			//Check for EOJ
			if (nextLine.startsWith(JOB_END)) {
									
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
			else if (nextLine.startsWith(JOB_START)) {
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
					
					if (programLine.equals(JOB_END) 
							|| programLine.equals(JOB_START)) {
						trace.info("breaking on "+programLine);
						break;
					}
					else if (programLine.equals(DATA_START)) {
						//trace.info("start cycle "+incrementCycleCount());
						trace.info("data start on "+programLine);
						trace.fine("Memory contents: " + cpu.dumpMemory());
						trace.fine("CPU: "+cpu.toString());
						p = new Process(id, maxTime, maxPrints, br, wr);
						p.startExecution();
						processCount++;
						trace.finer("<-- DATA_START");
						return retval;
					} else {
						framenum = cpu.allocatePage(pagenum);
						cpu.writeFrame(framenum, programLine);
					}
					pagenum+=1;
					programLine = br.readLine();
				}
			} else {
				trace.warning("skipping data line:" + nextLine);
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
	 * \dot digraph kernel_read {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * read_0 [label="get operand"];
	 * read_2 [label="set program interrupt"];
	 * read_6 [label="kernelStatus = ABORT"];
	 * read_6a [label="kernelStatus = ABORT"];
	 * read_10 [label="kernelStatus = INTERRUPT"];
	 * read_10a [label="kernelStatus = INTERRUPT"];
	 * read_14 [label="kernelStatus = CONTINUE"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"]; 
	 * read_1 [label="if the next line has not been buffered"];
	 * read_3 [label="does the line read contain $EOJ"];
	 * read_5 [label="has the time exceeded"];
	 * read_7 [label="is there a program interrupt"];
	 * read_9 [label="catch hardwareInterrupt"];
	 * 
	 * node [shape="parallelogram",color="brown1",sytle="filled",fontsize="8",fontname="Helvetica"];
	 * read_4 [label="read line"];
	 * read_12 [label="write data line to memory"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * read_begin [label="begin", color="chartreuse"];
	 * read_return [label="return kernelStatus", color="firebrick1"];
	 * 
	 * { rank=same; read_3 read_4; };
	 * { rank=same; read_5 read_6; };
	 * { rank=same; read_6a read_7; };
	 * { rank=same; read_10 read_12; };
	 * 
	 * read_begin -> read_14 -> read_0 -> read_2;
	 * read_2 -> read_1; read_1 -> read_4 [label="true"]; 
	 * read_1 -> read_3 [label="false"];
	 * read_4 -> read_3;
	 * read_3 -> read_6 [label="true"];
	 * read_6 -> read_5;
	 * read_3 -> read_5 [label="false"];
	 * read_5 -> read_6a [label="true"];
	 * read_6a -> read_7;
	 * read_5 -> read_7 [label="false"];
	 * read_7 -> read_10 [label="false"];
	 * read_7 -> read_12 [label="true"];
	 * read_12 -> read_9;
	 * read_9 -> read_10a [label="true"];
	 * read_9 -> read_return [label="false"];
	 * read_10 -> read_return;
	 * read_10a -> read_return; }
	 * \enddot
	 * 
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
			trace.fine("data line from input file: "+lastLineRead);			
			lineBuffered = true;
		}
		else {
			trace.fine("using buffered line: "+lastLineRead);
		}
		
		trace.info("operand:"+irValue+" pi="+cpu.getPi().getValue());
		// If next data card is $END, TERMINATE(1)
		if (lastLineRead.startsWith(JOB_END)){
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
	 * \dot digraph kernel_write {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * write_0 [label="get operand"];
	 * write_2 [label="set program interrupt"];
	 * write_4 [label="kernelStatus = ABORT"];
	 * write_6 [label="kernelStatus = INTERRUPT"];
	 * write_6a [label="kernelStatus = INTERRUPT"];
	 * write_6b [label="kernelStatus = INTERRUPT"]; 
	 * write_10 [label="kernelStatus = CONTINUE"];
	 * 
	 * node [shape="diamond",color="blue",,fontsize="8",fontname="Helvetica"];
	 * write_1 [label="if the print count exceeded"];
	 * write_3 [label="has the time exceeded"];
	 * write_5 [label="is there a program interrupt"];
	 * write_7 [label="catch hardwareInterrupt"];
	 * 
	 * node [shape="parallelogram",color="brown1",sytle="filled",fontsize="8",fontname="Helvetica"];
	 * write_8 [label="read from memory to IO buffer"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"]; 
	 * write_begin [label="begin", color="chartreuse"]; 
	 * write_return [label="return kernelStatus", color="firebrick1"];
	 * 
	 * { rank=same; write_6 write_3; };
	 * { rank=same; write_6a write_8; };

	 * 
	 * write_begin -> write_10 -> write_1;
	 * write_1 -> write_6 [label="true"];
	 * write_6 -> write_3;
	 * write_1 -> write_3 [label="false"];
	 * write_3 -> write_4 [label="true"];
	 * write_3 -> write_0 [label="false"];
	 * write_0 -> write_2;
	 * write_2 -> write_5;
	 * write_4 -> write_5;
	 * write_5 -> write_8 [label="false"];
	 * write_5 -> write_6a [label="true"];
	 * write_8 -> write_7;
	 * write_7 -> write_6b [label="true"];
	 * write_7 -> write_return [label="false"];
	 * write_6b -> write_return;
	 * write_6a -> write_return;
	 * } \enddot
	 * @return KernelStatus set to CONTINUE, INTERRUPT or ABORT.
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
	 * \dot digraph kernel_terminate {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * terminate_0 [label="kernelStatus = CONTINUE"];
	 * terminate_2 [label="free page table"];
	 * terminate_6 [label="load the next program"];
	 * 
	 * node [shape="parallelogram",color="brown1",sytle="filled",fontsize="8",fontname="Helvetica"];
	 * terminate_4 [label="write new lines to output buffer"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * terminate_begin [label="begin", color="chartreuse"];
	 * terminate_return [label="return kernelStatus", color="firebrick1"];
	 * 
	 * terminate_begin -> terminate_0 -> terminate_2 -> terminate_4 ->
	 * terminate_6 -> terminate_return;
	 * } \enddot
	 * 
	 * @throws IOException
	 * @return KernelStatus set to CONTINUE or whatever is passed from load
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
	 * 
	 * @return
	 */
	public CPU getCpu() {
		return cpu;
	}

	/**
	 * Master execution cycle
	 * \dot digraph kernel_masterMode {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * { node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * rank=min; masterMode_begin [label="begin", color="chartreuse"]; };
	 * 
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * masterMode_notDone [label="done = false"];
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * masterMode_done[label="Are we done?"];
	 * 
	 * { rank=same; 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * masterMode_hardwareInterrupt[label="catch hardware interrupt?"]; }; 
	 * node [shape="box",fontsize="8",fontname="Helvetica"]; 
	 * masterMode_slaveMode[label="slave mode"];
	 * masterMode_interruptHandler [label="done = interruptHandler"];
	 * 
	 * { node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"]; 
	 * rank=max; 
	 * masterMode_return [label="return done", color="firebrick1"]; };
	 * 
	 * masterMode_begin -> masterMode_notDone -> masterMode_done;
	 * masterMode_done -> masterMode_slaveMode [label="false"];
	 * masterMode_slaveMode -> masterMode_hardwareInterrupt;
	 * masterMode_hardwareInterrupt -> masterMode_interruptHandler [label="true"];
	 * masterMode_hardwareInterrupt -> masterMode_done [label="false"];
	 * masterMode_interruptHandler -> masterMode_done;
	 * masterMode_done -> masterMode_return [label="true"];
	 * } \enddot
	 * 
	 * @throws IOException
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
	 * \dot digraph kernel_slaveMode {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * slaveMode_fetch[label="fetch"];
	 * slaveMode_increment[label="increment"];
	 * slaveMode_execute[label="execute"];
	 * slaveMode_time[label="check process time count and increment"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * slaveMode_begin [label="begin", color="chartreuse"];
	 * slaveMode_return [label="return done", color="firebrick1"];
	 * 
	 * slaveMode_begin -> slaveMode_fetch-> slaveMode_increment ->
	 * slaveMode_execute -> slaveMode_time -> slaveMode_return;
	 * } \enddot
	 * 
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
	 * Check if there already exists and error in the current process. If one
	 * does not exist set new status effectively clearing existing status and
	 * setting errorInProcess to true If one exists append new error to existing
	 * status otherwise clear
	 * 
	 * @param err
	 */
	public void setError(int err) {
		trace.finer("-->");
		trace.fine("setting err="+err);
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
	 * Increments the master/slave cycle count
	 */
	private int incrementCycleCount() {
		return cycleCount++;
	}
}
