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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import emu.hw.Buffer;
import emu.hw.Buffer.BufferState;
import emu.hw.CPU;
import emu.hw.CPUState;
import emu.hw.CPUState.Interrupt;
import emu.hw.Channel1;
import emu.hw.Channel2;
import emu.hw.Channel3;
import emu.hw.HardwareInterruptException;
import emu.os.ChannelTask.TaskType;
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
	/**
	 * Reads data from input file (card reader)
	 */
	Channel1 ch1;
	/**
	 * Writes data to output file (printer)
	 */
	Channel2 ch2;
	/**
	 * Transfers data between primary and secondary storage
	 */
	Channel3 ch3;
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
	 * The program currently being input spooled 
	 */
	PCB inputPCB;
	/**
	 * Programs ready to execute 
	 */
	Queue<PCB> readyQueue;
	/**
	 * Programs needing IO
	 */
	Queue<PCB> ioQueue;
	/**
	 * Programs ready to terminate (output spool)
	 */
	Queue<PCB> terminateQueue;
	/**
	 * Programs needing to swap memory
	 */
	Queue<PCB> swapQueue;
	/**
	 * Programs waiting for memory resources
	 */
	Queue<PCB> memoryQueue;
	/**
	 * Buffers used for I/O spooling
	 */
	List<Buffer> buffers;
	/**
	 * Maximum number of buffers
	 */
	private int maxBuffers = 4;
	
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
		UNKNOWN    (-1,"Unknown Error"),
		NO_ERROR  ( 0,"No Error"),
		OUT_OF_DATA   ( 1,"Out of Data"),
		LINE_LIMIT_EXCEEDED   ( 2,"Line Limit Exceeded"),
		TIME_LIMIT_EXCEEDED ( 3,"Time Limit Exceeded"),
		OPERATION_CODE_ERROR  ( 4,"Operation Code Error"),
		OPERAND_FAULT  ( 5,"Operand Fault"),
		INVALID_PAGE_FAULT   ( 6,"Invalid Page Fault");
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
	public static void initTrace(String[] args) {
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

		processCount = 0;
		inMasterMode = true;

		//Init I/O
		BufferedReader input = new BufferedReader(new FileReader(inputFile));
		BufferedWriter output = new BufferedWriter(new FileWriter(outputFile));
		
		//Init Channels
		ch1 = new Channel1(5,cpu,input);
		ch2 = new Channel2(5,cpu,output);
		ch3 = new Channel3(2,cpu);
		
		//Init Queues
		readyQueue = new LinkedList<PCB>();
		ioQueue =  new LinkedList<PCB>();
		terminateQueue = new LinkedList<PCB>();
		swapQueue = new LinkedList<PCB>();
		memoryQueue = new LinkedList<PCB>();
		
		//Init Buffers
		buffers = new LinkedList<Buffer>();
		for (int i=0; i<maxBuffers;i++) {
			buffers.add(new Buffer());
		}
		

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
//			cpu.initPageTable();
//			cpu.allocatePage(0);
//			cpu.writePage(0, bootSector);
			//Initialize input spooling
			cpu.setIOi(Interrupt.IO_CHANNEL_1.getValue());
			masterMode();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
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
	 * @throws HardwareInterruptException 
	 */
	public boolean interruptHandler() throws IOException, HardwareInterruptException {
		boolean retval = false;
		inMasterMode = true;
		KernelStatus status = KernelStatus.INTERRUPT;
		
		/*
		 * This is in a loop because new interrupts may be generated while processing request from
		 * slaveMode. KernelStatus is used to control flow through this loop.
		 */
		trace.finer("-->");
		//trace.fine("Physical Memory:\n"+cpu.dumpMemory());
		//while (status == KernelStatus.INTERRUPT) {
			//trace.info("start cycle "+incrementCycleCount());
			trace.info(""+cpu.dumpInterrupts());
			trace.fine("Kernel status="+status);
	
			//TODO here for testing, this may change.
			switch (cpu.getIOi()) {
				case CLEAR:
					break;
				case IO_CHANNEL_1:
					proccesCh1();
					break;
				case IO_CHANNEL_3:
					processCh3();
					break;
				case IO_CHANNEL_13:
					proccesCh1();
					processCh3();
					break;
			}
			
			assignChannel3();
			
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
					boolean valid = cpu.getMMU().validatePageFault(cpu.getIr());
					if (valid){
						int frame = cpu.allocatePage(cpu.getOperand() / 10); //TODO cleaner way to determine page #?
						trace.fine("frame "+frame+" allocated for page "+cpu.getOperand());
						cpu.setPi(Interrupt.CLEAR);
						cpu.decrement();
						status = KernelStatus.CONTINUE;
					} else {
						setError( ErrorMessages.INVALID_PAGE_FAULT.getErrCode());
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
//			if (cpu.getIOi().equals(Interrupt.IO)){
//				status = KernelStatus.ABORT;
//				cpu.setIOi(Interrupt.CLEAR);
//			}
			
			/*
			 * This is handles a programming error i.e. bugs in setting Interrupts
			 */
			if (cpu.getPi().equals(Interrupt.WRONGTYPE)
					|| cpu.getTi().equals(Interrupt.WRONGTYPE)
					|| cpu.getSi().equals(Interrupt.WRONGTYPE)){
				return true;
			}
			trace.fine("Status "+cpu.dumpInterrupts());
			
			/*
			 * Normally the loop will restart and eventually find terminate.
			 * No need to reiterate through loop if its going to call terminate.
			 */
			if (status == KernelStatus.ABORT) {
				status = terminate();
			}
			trace.fine("End of Interrupt Handling loop "+status);
		//}

		// Tell slaveMode that there are no more programs to run
		if (status == KernelStatus.TERMINATE)
			retval = true;
		
		trace.fine(retval+"<--");
		return retval;
	}
	
	/**
	 * Process the channel 3 interrupt
	 */
	private void processCh3() {
		trace.finest("-->");
		//Switch over the possible tasks
		switch (ch3.getTask().getType()) {
			case GD:
				//TODO Implement
				break;
			case PD:
				//TODO Implement
				break;
			case INPUT_SPOOLING:
				if (inputPCB.isProgramCardsToFollow()) {
					inputPCB.addInstructionTrack(ch3.getTask().getTrack());
				}
				else {
					inputPCB.addDataTrack(ch3.getTask().getTrack());
				}
				ch3.getTask().getBuffer().setEmpty();
				break;
			case OUTPUT_SPOOLING:
				//TODO Implement
				break;
			case SWAP_IN:
				//TODO Implement
				break;
			case SWAP_OUT:
				//TODO Implement
				break;
			default:
				trace.severe("Unknown task");
		}
		
		trace.finest("<--");
	}
	
	/**
	 * Assign the next task to ch3
	 * @throws HardwareInterruptException 
	 */
	private void assignChannel3 () throws HardwareInterruptException {
		trace.finest("-->");
		
		if (ch3.isBusy()) {
			trace.info("channel 3 is busy");
			return;
		}
		ChannelTask task = new ChannelTask();
		
		if (getInputFullBuffer() != null) {
			task.setBuffer(getInputFullBuffer());
			task.setType(ChannelTask.TaskType.INPUT_SPOOLING);
			//TODO needs to be allocate drum, using a frame number for now;
			int track = cpu.getMMU().allocateFrame(); 
			task.setTrack(track);
			ch3.start(task);
		}
		else {
			trace.info("nothing to do for ch3!");
		}
		
		trace.finest("<--");
	}

	/**
	 * Process the ch1 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void proccesCh1() throws HardwareInterruptException {
		trace.finer("-->");
		
		if (getEmptyBuffer() != null) {
			ChannelTask task = new ChannelTask();
			task.setBuffer(getEmptyBuffer());
			task.setType(TaskType.INPUT_SPOOLING);
			ch1.start(task);
		}
		else {
			trace.warning("No empty buffers!");
		}
		
		//Get the ifb
		Buffer b = getInputFullBuffer();
		if (b != null) {
			String data = b.getData();
			
			//If its a control card, process accordingly
			if (data.startsWith(PCB.JOB_START)) {
				processAMJ(b);
			}
			else if (data.startsWith(PCB.DATA_START)) {
				processDTA(b);
			}
			else if (data.startsWith(PCB.JOB_END)) {
				processEOJ(b);
			}
		}
		else {
			trace.fine("No ifb's");
		}
		
		trace.finer("<--");
	}
	
	/**
	 * Parse the job card from an ifb, create the PCB.
	 */
	private void processAMJ(Buffer b) {

		String jobCard = b.getData();
		trace.info("Loading job:"+jobCard);
		
		//Parse Job Data
		String id = jobCard.substring(4, 8);
		int maxTime = Integer.parseInt(jobCard.substring(8, 12));
		int maxPrints = Integer.parseInt(jobCard.substring(12, 16));
		
		//Create PCB
		inputPCB = new PCB(id, maxTime, maxPrints);

		//Create a CPU state;
		CPUState cpuState = new CPUState();
		
		//Init a page table
		cpuState.setPtr(cpu.getMMU().allocateFrame());
		
		inputPCB.setCpuState(cpuState);

		//Return buffer to ebq
		b.setEmpty();

	}
	private void processDTA(Buffer b) {
		
		trace.info("$DTA for "+inputPCB.getId());
		inputPCB.setProgramCardsToFollow(false);
		b.setEmpty();

	}
	
	private void processEOJ(Buffer b) {

		String eojCard = b.getData();
		String id = eojCard.substring(4, 8);
		trace.fine("Finished spooling in job "+id);
		
		//Once the EOJ is reached, move the PCB to the ready queue.
		readyQueue.add(inputPCB);
		
		//Return buffer to ebq
		b.setEmpty();

	}

//	/**
//	 * 
//	 * @throws IOException
//	 */
//	private void checkForCurrentProcess() throws IOException {
//		//Check for current process
//		if (p != null && p.isRunning()) {
//			trace.warning("Process "+p.getId()+" never finished");
//			setError(ErrorMessages.UNKNOWN.getErrCode());
//			finishProccess();
//		}
//		
//	}

	/**
	 * Processing of a read from the GD instruction
	 * @return
	 * @throws IOException
	 */
	public KernelStatus read() throws IOException{
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");
		trace.fine("Entering KernelStatus Read, who reads a line");
		
		PCB p = getCurrentProcess();
		
		// get memory location and set interrupt if one exists
		int irValue = cpu.getOperand();
		cpu.setPi(irValue);

		// read next data card
		if (!lineBuffered) {
			//lastLineRead = br.readLine();
			trace.fine("data line from input file: "+lastLineRead);			
			lineBuffered = true;
		}
		else {
			trace.fine("using buffered line: "+lastLineRead);
		}
		
		trace.info("operand:"+irValue+" pi="+cpu.getPi().getValue());
		// If next data card is $END, TERMINATE(1)
		if (lastLineRead.startsWith(PCB.JOB_END)){
			setError(1);
			finishProccess();
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
					trace.info("HW interrupt:"+cpu.dumpInterrupts());
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
		PCB p = getCurrentProcess();
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
				//p.write(cpu.readBlock(irValue));//TODO move to IO queue
//				try {
//					
//				} catch (HardwareInterruptException e) {
//					trace.info("HW interrupt:"+cpu.dumpInterrupts());
//					retval = KernelStatus.INTERRUPT;
//				}
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
		//cpu.clearInterrupts();

		//Write 2 empty lines to the output
		//wr.write("\n\n");
		
		// Load the next user program
		//retval = load();
		
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
	public void masterMode() throws IOException, HardwareInterruptException {
		trace.finer("-->");
		inMasterMode = false;
		boolean done = false;
		while (!done) {
			trace.info("****************************************************************************");
//			try {
//				trace.info("start cycle "+incrementCycleCount());
//				slaveMode();
//			} catch (HardwareInterruptException hie) {
//				trace.info("start cycle "+incrementCycleCount());
//				trace.info("HW Interrupt from slave mode");
//				trace.fine(cpu.dumpInterrupts());
//				//done = interruptHandler();
//				done = true;
//				inMasterMode = false;
//			}

			trace.info("start cycle "+incrementCycleCount());
			slaveMode();
			interruptHandler();
			
			done = !ch1.isBusy();
		}
		
		trace.finer("<--");
	}
	
	/**
	 * Slave execution cycle
	 * @throws HardwareInterruptException
	 */
	public void slaveMode() throws HardwareInterruptException {
		trace.info("start slave mode ");
		
		//TODO CPU commented out for testing
		//cpu.fetch();
		//cpu.increment();
		
		//Increment channel clocks 
		ch1.increment();
		ch2.increment();
		ch3.increment();
		
		//cpu.execute();
		PCB p = getCurrentProcess();
		if (p != null) {
			p.incrementTimeCountSlave();			
		}
	}
	
	/**
	 * TODO each of these block need to be queued to ch3 as output spool tasks
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public void finishProccess() throws IOException {
		trace.finer("-->");
//		wr.write(p.getId()+" "+p.getTerminationStatus()+"\n");
//		wr.write(cpu.getState());
//		wr.write("    "+p.getTime()+"    "+p.getLines());
//		wr.newLine();
//		wr.newLine();
//		wr.newLine();
//		
//		ArrayList<String> buf = p.getOutputBuffer();
//		for (String line : buf) {
//			 wr.write(line);
//			 wr.newLine();
//		}
//		wr.flush();
//		p.terminate();
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
		trace.fine("setting err="+err);
		errMsg = ErrorMessages.set(err);
		PCB p = getCurrentProcess();
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
	
	/**
	 * Gets the next empty buffer
	 * @return An empty buffer, null if no empty buffers exist
	 */
	private Buffer getEmptyBuffer() {
		return getBufferOfState(BufferState.EMPTY);
	}
	
	/**
	 * Gets the next input-full buffer
	 * @return An input-full buffer, null if no input-full buffers exist
	 */
	private Buffer getInputFullBuffer() {
		return getBufferOfState(BufferState.INPUT_FULL);
	}
	
	/**
	 * Gets the next output full buffer
	 * @return An output-full buffer, null if no output-full buffers exist
	 */
	private Buffer getOutputFullBuffer() {
		return getBufferOfState(BufferState.OUTPUT_FULL);
	}
	
	/**
	 * Gets the next of the given state
	 * @return The next buffer of the given state, null if none exist
	 */
	private Buffer getBufferOfState(BufferState state) {
		trace.finest("-->");
		trace.finer(""+buffers);
		trace.fine(buffers.size()+" buffers");
		Buffer returnBuffer = null;
		for (Buffer b : buffers) {
			if (b.getState().getCurrent().equals(state.getStateName())) {
				returnBuffer = b;
			}
		}
		trace.fine("returning "+state.getStateName()+" buffer:"+returnBuffer);
		trace.finest("<--");
		return returnBuffer;
	}
	/**
	 * Returns the PCB at the head of the ready queue
	 * @return
	 */
	private PCB getCurrentProcess() {
		return readyQueue.peek();
	}
}
