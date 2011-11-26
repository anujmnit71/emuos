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
import emu.os.PCB.ProcessStates;
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
	 * Raised interrupts
	 */
	private int raisedInterrupts = 0;

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

	private enum ProcessQueues {
		READYQ,IOQ,TERMINATEQ,SWAPQ,MEMORYQ
	}

	/**
	 * table containing error messages
	 */
	ErrorMessages errMsg;

	public enum ErrorMessages {
		UNKNOWN               (-1,"Unknown Error"),
		NO_ERROR              ( 0,"No Error"),
		OUT_OF_DATA           ( 1,"Out of Data"),
		LINE_LIMIT_EXCEEDED   ( 2,"Line Limit Exceeded"),
		TIME_LIMIT_EXCEEDED   ( 3,"Time Limit Exceeded"),
		OPERATION_CODE_ERROR  ( 4,"Operation Code Error"),
		OPERAND_FAULT         ( 5,"Operand Fault"),
		INVALID_PAGE_FAULT    ( 6,"Invalid Page Fault"),
		OUT_OF_DRUM_MEMORY    ( 7,"No more drum tracks available");
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
			mainLoop();
		} catch (Exception e) {
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
	 * Master execution cycle 
	 * @throws HardwareInterruptException
	 * @throws IOException 
	 * @throws SoftwareInterruptException
	 */
	public void mainLoop() throws IOException, HardwareInterruptException {
		trace.finer("-->");
		boolean done = false;
		while (!done) {
			trace.info("start cycle "+incrementCycleCount());
			simulateHardware();
			if (raisedInterrupts > 0)
			{
				trace.info("----------------------------------------------------------------------------");
				done = masterMode();
			}
			trace.info("****************************************************************************");
			slaveMode();
			// for testing purpose
			if (cycleCount > 100)
				done = true;

		}

		trace.finer("<--");
	}

	public void slaveMode() throws HardwareInterruptException {
		trace.info("start slave mode ");
		try {
			cpu.fetch();
			cpu.increment();
			cpu.execute();
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
		}
	}

	/**
	 * Slave execution cycle
	 * @throws HardwareInterruptException
	 */
	public void simulateHardware() throws HardwareInterruptException {
		trace.info("simulate hardware");

		// This is needed to get the OS started.
		if (!cpu.getIOi().equals(Interrupt.CLEAR))
		{
			raisedInterrupts++;
		}

		try {
			ch1.increment();
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
		}

		try {
			ch2.increment();
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
		}

		try {
			ch3.increment();
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
		}

		try {
			PCB p = getCurrentProcess();
			if (p != null) {
				p.incrementTimeCount();			
			}
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
		}		
	}

	/**
	 * Called when control needs to be passed back to the OS
	 * though called masterMode this is more of and interrupt handler for the OS
	 * Interrupts processed in two groups TI = 0(CLEAR) and TI = 2(TIME_ERROR)
	 * @throws IOException
	 * @throws HardwareInterruptException 
	 */
	public boolean masterMode() throws IOException, HardwareInterruptException {
		boolean retval = false;
		KernelStatus status = KernelStatus.INTERRUPT;

		/*
		 * This is handles a programming error i.e. bugs in setting Interrupts
		 */
		if (cpu.getPi().equals(Interrupt.WRONGTYPE)
				|| cpu.getTi().equals(Interrupt.WRONGTYPE)
				|| cpu.getSi().equals(Interrupt.WRONGTYPE)
				|| cpu.getIOi().equals(Interrupt.WRONGTYPE)){
			return true;
		}		

		trace.finer("-->");
		trace.info(""+cpu.dumpInterrupts());
		trace.fine("Kernel status="+status);

		if (!(cpu.getTi().equals(Interrupt.CLEAR)))
		{
			status = handleTimeInterrupt();
			raisedInterrupts--;
		}

		if (!(cpu.getPi().equals(Interrupt.CLEAR)) && !status.equals(KernelStatus.ABORT))
		{
			status = handleProgramInterrupt(false);
			raisedInterrupts--;
		}

		if (!(cpu.getPi().equals(Interrupt.CLEAR)) && !status.equals(KernelStatus.ABORT))
		{
			status = handleServiceInterrupt(false);
			raisedInterrupts--;
		}

		//TODO here for testing, this may change.
		trace.info("+++IOI: "+cpu.getIOi());

		// Process channel 2
		if ((cpu.getIOi().getValue() & 2 ) == 2)
		{
			processCh2();
			raisedInterrupts--;
		}

		// Process channel 1
		if ((cpu.getIOi().getValue() & 1 ) == 1)
		{
			processCh1();
			raisedInterrupts--;
		}

		// Process channel 3
		if ((cpu.getIOi().getValue() & 4 ) == 4)
		{
			processCh3();
			raisedInterrupts--;
		}

		assignChannel3();
		trace.fine("Status "+cpu.dumpInterrupts());

		/*
		 * Normally the loop will restart and eventually find terminate.
		 * No need to reiterate through loop if its going to call terminate.
		 */
		//if (status == KernelStatus.ABORT) {
		//	contextSwitch(ProcessQueues.TERMINATEQ);
		//}


		trace.fine("End of Interrupt Handling"+status);

		dispatch();
		// Tell mainLoop that there are no more programs to run
		if (status == KernelStatus.TERMINATE)
			retval = true;

		trace.fine(retval+"<--");
		return retval;
	}

	private KernelStatus handleProgramInterrupt(boolean timeError) {
		KernelStatus retval = KernelStatus.CONTINUE;
		PCB p = getCurrentProcess();
		if (timeError)
		{
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
				setError(Interrupt.OPERAND_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE);
				break;
			case OPERATION_ERROR:
				setError(Interrupt.OPERATION_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE);
				break;
			case PAGE_FAULT:
				p.setState(ProcessStates.TERMINATE);
				break;
			}
			
		}
		else
		{
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
				p.setState(ProcessStates.TERMINATE);
				break;
			case OPERATION_ERROR:
				setError( Interrupt.OPERATION_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE);
				break;
			case PAGE_FAULT:
				boolean valid = cpu.getMMU().validatePageFault(cpu.getIr());
				if (valid){
					if (p != null) {
						int frame = cpu.allocatePage(cpu.getOperand() / 10); //TODO cleaner way to determine page #?
						trace.fine("frame "+frame+" allocated for page "+cpu.getOperand());
						cpu.decrement();
					}
				} else {
					setError( ErrorMessages.INVALID_PAGE_FAULT.getErrCode());
					p.setState(ProcessStates.TERMINATE);
				}
				break;
			}
		}

		if (!(cpu.getPi().equals(Interrupt.CLEAR)))
			cpu.setPi(Interrupt.CLEAR);
		
		if (p != null)
			trace.info("+++ PCB state = "+p.getState()+" next = "+p.getNextState());
		
		return retval;
	}

	private KernelStatus handleServiceInterrupt(boolean timeError)
	{
		KernelStatus retval = KernelStatus.CONTINUE;
		PCB p = getCurrentProcess();
		if (timeError)
		{
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
				p.setTerminationStatus(ErrorMessages.TIME_LIMIT_EXCEEDED.getMessage());
				p.setState(ProcessStates.TERMINATE);				
				break;
			case WRITE:
				p.setState(ProcessStates.IO_WRITE);
				//contextSwitch(ProcessQueues.IOQ);
				p.setTerminationStatus(ErrorMessages.TIME_LIMIT_EXCEEDED.getMessage());
				p.setNextState(ProcessStates.TERMINATE);
				break;
			case TERMINATE:
				//	Dump memory
				trace.finer("\n"+cpu.dumpMemory());
				p.setState(ProcessStates.TERMINATE);
				break;
			}
		}
		else
		{
			/*
			 * Handle Service Interrupt
			 * TI SI
			 * -- --
			 * 0  1  READ
			 * 0  2  WRITE
			 * 0  3  TERMINATE(0)
			 */
			switch (cpu.getSi()) {
			case READ:
				trace.finest("Si interrupt read");
				p.setState(ProcessStates.IO_READ);
				break;
			case WRITE:
				trace.finest("Si interrupt write");
				p.setState(ProcessStates.IO_WRITE);
				break;
			case TERMINATE:
				//	Dump memory
				trace.fine("Case:Terminate");

				trace.fine("Memory contents: " + cpu.dumpMemory());
				p.setState(ProcessStates.TERMINATE);
				break;
			}
		}
		if (!(cpu.getSi().equals(Interrupt.CLEAR)))
			cpu.setSi(Interrupt.CLEAR);
		
		if (p != null)
			trace.info("+++ PCB state = "+p.getState()+" next = "+p.getNextState());
		
		return retval;
	}

	private KernelStatus handleTimeInterrupt() throws IOException
	{
		KernelStatus retval = KernelStatus.CONTINUE;
		PCB p = getCurrentProcess();
		switch (cpu.getTi()) {

		case TIME_QUANTUM:
			p.setState(ProcessStates.READY);
			break;
		case TIME_ERROR:
			retval = handleServiceInterrupt(true);

			if (retval.equals(KernelStatus.CONTINUE))
				retval = handleProgramInterrupt(true);

			/*
			 * Still have to handle a plain old Time Interrupt
			 */
			setError(Interrupt.TIME_ERROR.getErrorCode());
			p.setState(ProcessStates.TERMINATE);
			break;
		}
		if (!(cpu.getTi().equals(Interrupt.CLEAR)))
			cpu.setTi(Interrupt.CLEAR);
		
		if (p != null)
			trace.info("+++ PCB state = "+p.getState()+" next = "+p.getNextState());
		
		return retval;
	}

	/**
	 * Process the channel 3 interrupt
	 */
	private void processCh3() {
		trace.finest("-->");
		//process the current task
		//Switch over the possible tasks
		switch (ch3.getTask().getType()) {
		case GD:
			getDataTask();
			break;
		case PD:
			putDataTask();
			break;
		case INPUT_SPOOLING:
			inputSpoolingTask();
			break;
		case OUTPUT_SPOOLING:
			outputSpoolingTask();
			break;
		case SWAP_IN:
			//TODO Implement
			//SwapInTask();
			break;
		case SWAP_OUT:
			//TODO Implement
			//SwapOutTask();
			break;
		default:
			trace.severe("Unknown task");
		}

		//		//Decrement IOi by 4 to signal channel handled
		//		trace.finer("Decrementing IOi by 4");
		//		cpu.setIOi(-4);//BJD Not needed, already done in each channels start() method.

		trace.finest("<--");
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
	private KernelStatus getDataTask() {
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");

		PCB ioPCB = ioQueue.remove();
		schedule(ioPCB);

		trace.finer(retval+"<--");
		return retval;
	}

	/**
	 * Processing of a write from the PD instruction
	 * @return
	 */
	private KernelStatus putDataTask(){
		KernelStatus retval = KernelStatus.CONTINUE;
		trace.finer("-->");

		PCB ioPCB = ioQueue.remove();
		schedule(ioPCB);

		trace.finer(retval+"<--");
		return retval;
	}

	private void outputSpoolingTask() {
		//release track
		cpu.getMMU().getDrum().freeTrack(ch3.getTask().getTrack());

		//Get the PCB being output spooled.
		PCB p = terminateQueue.peek();
		//this is checked when the task is assigned to channel 3
		//p.incrementPrintCount();

		//Check if output spooling is complete.
		if (p.outputComplete()) {
			trace.fine("Output Spooling complete for pid:"+p.getId());
			PCB terminatedPCB = terminateQueue.remove();

			//Terminate the process
			terminatedPCB.terminate();

			Buffer b = getEmptyBuffer();
			if (b != null) {
				//Prepare blank lines for print in between program output
				b.setData("\n\n");					
				b.setOutputFull();
			}
			else {
				//TODO what now?
			}
		}		
	}

	private void inputSpoolingTask() {
		trace.info("+++Adding track"+ch3.getTask().getTrack());
		if (inputPCB.isProgramCardsToFollow()) {
			trace.info("+++adding instruction track");
			inputPCB.addInstructionTrack(ch3.getTask().getTrack());
		}
		else {
			trace.info("+++adding data track");
			inputPCB.addDataTrack(ch3.getTask().getTrack());
		}
		ch3.getTask().getBuffer().setEmpty();		
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

		if (swapQueue.size() > 0) {
			PCB swapPCB = swapQueue.peek();
			// TODO: swapping: PCB contains swap out and/or swap in information
		}
		else if (terminateQueue.size() > 0) {

			Buffer eb = getEmptyBuffer();
			if (eb != null) {
				PCB termPCB = terminateQueue.peek();

				if (termPCB.getHeaderLinedPrinted() == 0) {
					eb.setData(termPCB.getId()+" "+termPCB.getTerminationStatus());
					termPCB.incrementHeaderLinedPrinted();
					eb.setOutputFull();
				}
				else if (termPCB.getHeaderLinedPrinted() == 1) {
					eb.setData(cpu.getState()+"    "+termPCB.getCurrentTime()+"    "+termPCB.getLines()+"\n\n");
					termPCB.incrementHeaderLinedPrinted();
					eb.setOutputFull();
				}
				else {
					int track = termPCB.getNextOutputTrack();
					//Set the track to read from
					if (track >= 0) {
						task.setType(ChannelTask.TaskType.OUTPUT_SPOOLING);
						task.setTrack(track);
						task.setBuffer(eb);
					}
				}	
			}
		}
		else if (ioQueue.size() > 0) {
			PCB ioPCB = ioQueue.peek();
			int irValue = 0;
			int track = 0;
			if (ioPCB.getState().equals(ProcessStates.IO_READ.getName()))
			{
				irValue = cpu.getOperand();
				cpu.setPi(irValue);
				track = ioPCB.getNextDataTrack();
				if (track >= 0) {
					setError(ErrorMessages.TIME_LIMIT_EXCEEDED.getErrCode());
					ioPCB.setNextState(ProcessStates.TERMINATE);
				}
				else
				{
					if (cpu.getPi() == Interrupt.CLEAR) {
						task.setTrack(track);
						task.setFrame(cpu.getMMU().getFrame(irValue));
						task.setType(TaskType.GD);
					} else {
						setError(ErrorMessages.OPERAND_FAULT.getErrCode());
						ioPCB.setState(ProcessStates.TERMINATE);
					}			
				}
			}
			else if (ioPCB.getState().equals(ProcessStates.IO_WRITE.getName()))
			{
				// Increment the line limit counter
				if (!ioPCB.incrementPrintCount()){
					setError(ErrorMessages.LINE_LIMIT_EXCEEDED.getErrCode());
					ioPCB.setNextState(ProcessStates.TERMINATE);
				} else {
					// get memory location and set exception if one exists
					irValue = cpu.getOperand();
					cpu.setPi(irValue);
					if (cpu.getPi() == Interrupt.CLEAR) {
						track = cpu.getMMU().allocateTrack();

						ioPCB.addDataTrack(track);
						task.setFrame(cpu.getMMU().getFrame(irValue));
						task.setType(ChannelTask.TaskType.PD);
					} else {
						setError(ErrorMessages.OPERAND_FAULT.getErrCode());
						ioPCB.setState(ProcessStates.TERMINATE);
					}			
				}
			}
		}
		else if (getInputFullBuffer() != null) {
			int track = cpu.getMMU().allocateTrack();

			task.setBuffer(getInputFullBuffer());
			task.setType(ChannelTask.TaskType.INPUT_SPOOLING);
			task.setTrack(track);
			trace.info("ch3:" + task.getType().toString());
		}
		else {
			trace.info("nothing to do for ch3!");
		}


		//If a task type was set, start the channel
		if (task.getType() != null) try {
			ch3.start(task);
		} catch (HardwareInterruptException e) {
			trace.severe("Tried to start a task on a channel that was busy");
			e.printStackTrace();
		}

		trace.finest("<--");
	}

	/**
	 * Process the ch1 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void processCh1() throws HardwareInterruptException {
		trace.finer("-->");

		//Get the ifb from the current task
		Buffer ifb = getInputFullBuffer();

		if (ifb != null) {
			String data = ifb.getData();
			//If its a control card, process accordingly
			if (data.startsWith(PCB.JOB_START)) {
				processAMJ(ifb);
			}
			else if (data.startsWith(PCB.DATA_START)) {
				processDTA(ifb);
			}
			else if (data.startsWith(PCB.JOB_END)) {
				processEOJ(ifb);
			}
			//System.exit(-1);
		}
		else {
			trace.fine("No ifb's");
		}

		//start another task for channel 1
		Buffer eb = getEmptyBuffer();
		if (eb != null) {
			ChannelTask task = new ChannelTask();
			task.setBuffer(eb);
			task.setType(TaskType.INPUT_SPOOLING);
			ch1.start(task);
		}
		else {
			trace.warning("No empty buffers!");
		}

		//Decrement IOi by 1 to signal channel 1 handled
		//		trace.finer("Decrementing IOi by 1");
		//		cpu.setIOi(-1);										//BJD Not needed, already done in each channels start() method.
		trace.finer("<--");
	}

	/**
	 * Process the ch2 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void processCh2() throws HardwareInterruptException {
		trace.finer("-->");

		// start another task for channel 2
		if (getOutputFullBuffer() != null) {
			ChannelTask task = new ChannelTask();
			task.setBuffer(getOutputFullBuffer());
			task.setType(TaskType.OUTPUT_SPOOLING);
			ch2.start(task);
		}
		else {
			trace.warning("No Outputfull buffers!");
		}

		//		//Decrement IOi by 2 to signal channel 2 handled
		//		trace.finer("Decrementing IOi by 2");
		//		cpu.setIOi(-2);										//BJD Not needed, already done in each channels start() method.
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
		inputPCB.setState(ProcessStates.SPOOL);

		//Return buffer to ebq
		b.setEmpty();

	}

	private void processDTA(Buffer b) {

		trace.info("$DTA for "+inputPCB.getId());
		inputPCB.setProgramCardsToFollow(false);
		b.setEmpty();

	}

	private void processEOJ(Buffer b) throws HardwareInterruptException {

		String eojCard = b.getData();
		String id = eojCard.substring(4, 8);
		trace.fine("Finished spooling in job "+id);
		//AMC

		trace.info("***\n"+cpu.getMMU().toString());
		trace.info("***"+inputPCB.toString());

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
		System.out.println(p);
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

	private void schedule(PCB pcb) {
		if(pcb.getNextState().equals(ProcessStates.TERMINATE.getName())) {
			pcb.setState(ProcessStates.TERMINATE);
			terminateQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.MEMORY.getName())) {
			pcb.setState(ProcessStates.MEMORY);
			memoryQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.IO_READ.getName())
				|| pcb.getNextState().equals(ProcessStates.IO_WRITE.getName())) {

			if (pcb.getNextState().equals(ProcessStates.IO_READ.getName()))
				pcb.setState(ProcessStates.IO_READ);
			else
				pcb.setState(ProcessStates.IO_WRITE);

			ioQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.SWAP.getName())) {
			pcb.setState(ProcessStates.SWAP);
			swapQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.READY.getName())) {
			pcb.setState(ProcessStates.READY);
			readyQueue.add(pcb);
		}
	}

	private void contextSwitch(ProcessQueues targetQ) {

		// Switches currently running process from head of ready queue to tail of the target queue
		PCB movePCB = readyQueue.remove();
		// Stores CPU status to the PCB that is being switched
		try {
			movePCB.setCpuState((CPUState) cpu.getCPUState().clone());
		} catch (CloneNotSupportedException e) {
			trace.log(Level.WARNING, "Failed to clone current CPU state", e);
		}
		switch (targetQ) {
		case READYQ: 
			readyQueue.add(movePCB);
			trace.fine("Swap to end of ReadyQ. ReadyQ contains "+readyQueue.size()+" processes");
			break;
		case IOQ:
			ioQueue.add(movePCB);
			trace.fine("Swap to IOQ. ReadyQ now contains "+readyQueue.size()+" processes. IOQ contains "+ioQueue.size()+" processes");
			break;
		case MEMORYQ:
			memoryQueue.add(movePCB);
			trace.fine("Swap to MemoryQ. ReadyQ now contains "+readyQueue.size()+" processes. MemoryQ contains "+memoryQueue.size()+" processes");
			break;
		case SWAPQ:
			swapQueue.add(movePCB);
			trace.fine("Swap to SwapQ. ReadyQ now contains "+readyQueue.size()+" processes. SwapQ contains "+swapQueue.size()+" processes");
			break;
		case TERMINATEQ:
			terminateQueue.add(movePCB);
			trace.fine("Swap to TerminateQ. ReadyQ now contains "+readyQueue.size()+" processes. TerminateQ contains "+terminateQueue.size()+" processes");
			break;
		}


		// Load CPU status from new head of ready queue to CPU
		if (readyQueue.size() >= 1)
			cpu.setState(getCurrentProcess().getCpuState());

	}

	private void dispatch() throws HardwareInterruptException {
		PCB pcb = getCurrentProcess();
		if (pcb != null) {
			if(pcb.getState().equals(ProcessStates.TERMINATE.getName())) {
				contextSwitch(ProcessQueues.TERMINATEQ);
			}
			else if(pcb.getState().equals(ProcessStates.MEMORY.getName())) {
				contextSwitch(ProcessQueues.MEMORYQ);
			}
			else if(pcb.getState().equals(ProcessStates.IO_READ.getName())
					|| pcb.getState().equals(ProcessStates.IO_WRITE.getName())) {
				contextSwitch(ProcessQueues.IOQ);
			}
			else if(pcb.getState().equals(ProcessStates.SWAP.getName())) {
				contextSwitch(ProcessQueues.SWAPQ);
			}
			else if(pcb.getState().equals(ProcessStates.READY.getName())) {
				contextSwitch(ProcessQueues.READYQ);
			}
			else if(pcb.getState().equals(ProcessStates.SPOOL.getName())) {
				initContext();
			}
		}
	}

	private void initContext() throws HardwareInterruptException {

		PCB pcb = getCurrentProcess();
		pcb.setState(ProcessStates.READY);
		
		if (readyQueue.size() == 1) {
			System.out.println("initialize PCB: " + pcb.getId());
			//Create a CPU state;
			CPUState cpuState = new CPUState();

			// load a page of instructions into memory
			int frame = cpu.allocatePage(0);
			cpuState.setPtr(CPU.getInstance().getPtr());
			System.out.println("*** instruction frame = "+frame);
			int instruction = inputPCB.getNextInstruction(0);
			System.out.println("*** instruction track = "+instruction);
			String program = cpu.getMMU().getDrum().read(instruction);
			System.out.println("*** instructions  = "+program);
			cpu.getMMU().writeFrame(frame, program);

			pcb.setCpuState(cpuState);
			cpu.setState(pcb.getCpuState());
		}
	}
}
