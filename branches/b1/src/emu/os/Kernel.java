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
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import emu.hw.Buffer;
import emu.hw.Buffer.BufferState;
import emu.hw.CPU;
import emu.hw.CPU.CPUStep;
import emu.hw.CPUState;
import emu.hw.CPUState.Interrupt;
import emu.hw.Channel1;
import emu.hw.Channel2;
import emu.hw.Channel3;
import emu.hw.Drum;
import emu.hw.HardwareInterruptException;
import emu.hw.PageTable;
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
	 * Dummy Kernel process
	 */
	PCB dummyPCB;
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
	boolean inputSpoolingComplete = false;
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
	 * Empty Buffer Q
	 */
	Queue<Buffer> ebq;
	/**
	 * Input full buffer queue
	 */
	Queue<Buffer> ifbq;
	/**
	 * Output full buffer queue
	 */
	Queue<Buffer> ofbq;
	/**
	 * Maximum number of buffers
	 */
	private int maxBuffers = 10;

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
	 * @throws CloneNotSupportedException 
	 */
	private Kernel(String inputFile, String outputFile) throws IOException, CloneNotSupportedException {

		trace.info("input:"+inputFile);
		trace.info("output:"+outputFile+"\n");

		//Init HW
		trace.info("Initializing EmuOS:");
		cpu = CPU.getInstance();
		cpu.setState(new CPUState());
//		cpu.initPageTable();       // why?

		dummyPCB = new PCB("kernel process", -1, -1);
		dummyPCB.setCpuState((CPUState) cpu.getCPUState().clone());

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
		ebq = new LinkedList<Buffer>();
		ifbq = new LinkedList<Buffer>();
		ofbq = new LinkedList<Buffer>();
		
		//buffers = new ArrayList<Buffer>();
		for (int i=0; i<maxBuffers;i++) {
			//buffers.add(i,new Buffer());
			ebq.add(new Buffer());
		}
		dumpBuffers();
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
	 * @throws CloneNotSupportedException 
	 */
	public static Kernel init(String inputFile, String outputFile) throws IOException, CloneNotSupportedException {
		ref = new Kernel(inputFile, outputFile);
		return ref;
	}

	/**
	 * Starts the OS by loading the HALT instruction into memory
	 * then calls to slaveMode to execute  
	 * @throws IOException
	 */

	public void boot() throws IOException {
//		trace.finer("-->");
		try {
			//trace.info("starting boot process");
			trace.info("Start cycle "+cycleCount);
			cpu.setIOi(Interrupt.IO_CHANNEL_1.getValue());
			mainLoop();
		} catch (Exception e) {
			trace.log(Level.SEVERE, "Uncaught exception in mainLoop", e);
		} finally {
			ch1.close();
			ch2.close();
			ch3.close();
			//Dump memory
			trace.fine("\n"+cpu.dumpMemory());
			//Dump Buffers
			dumpBuffers();
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
	 * @throws CloneNotSupportedException 
	 * @throws SoftwareInterruptException
	 */
	public void mainLoop() throws IOException, HardwareInterruptException, CloneNotSupportedException {
//		trace.finer("-->");
		boolean done = false;
		while (!done) {
			trace.info( "**************************************************************** Cycle: " + cycleCount);

			trace.info("Start master mode");
			
			if (raisedInterrupts > 0)
			{
//				trace.info("cycle :" + incrementCycleCount() + " ---------------------------------------------------------------");
				masterMode();
			}
			else {
				trace.info("  No interrupts for master mode to process");
				assignChannel3();
				assignChannel2();
			}
			
			slaveMode();
			
			simulateHardware();

			trace.info("loaded processes = " + processCount +
					"\n                  ready queue size = " + readyQueue.size() +
					"\n                   swap queue size = " + swapQueue.size() +
					"\n              terminate queue size = " + terminateQueue.size() +
					"\n                     io queue size = " + ioQueue.size() +
					"\n                 memory queue size = " + memoryQueue.size());
			if (inputSpoolingComplete && processCount == 0) {
				done = queuesEmpty();
				trace.info("Are all queues empty and input spooling complete ? " + done);
			}

			incrementCycleCount();
			//if (cycleCount > 450)
			//	done = true;
			dumpBuffers();
		}
//		trace.finer("<--");
	}

	private boolean queuesEmpty() {
		if (readyQueue.size() == 0
				&& ioQueue.size() == 0
				&& terminateQueue.size() == 0
				&& swapQueue.size() == 0
				&& memoryQueue.size() == 0 && inputPCB == null)
			return true;
		return false;
	}

	public void slaveMode() throws HardwareInterruptException {
		trace.info("Start slave mode");
		trace.fine("raised interrupts = " + raisedInterrupts + "");
		if (readyQueue.size() >= 1) // if there's a process to run this cycle
		{
			trace.info("CPU State:"+cpu.toString());
			try {
				cpu.fetch();
			}
			catch (HardwareInterruptException hie) {
				raisedInterrupts++;
				trace.info("  ++Cpu fetch raised interrupt = " + raisedInterrupts + "]");
				return;
			}
			cpu.increment();
			try {
				cpu.execute();
			}
			catch (HardwareInterruptException hie) {
				raisedInterrupts++;
				trace.info("  ++Cpu execute raised interrupt = " + raisedInterrupts + "]");
			}
		}
		else
			trace.info("  No process to run this cycle");
	}

	/**
	 * simulate Hardware cycle
	 * @throws HardwareInterruptException
	 */
	public void simulateHardware() throws HardwareInterruptException {
		trace.info("Simulate hardware");

		// This is needed to get the OS started.
		if (!cpu.getIOi().equals(Interrupt.CLEAR))
		{
			raisedInterrupts++;
			trace.info("  ++IO interrupt raised = [" + raisedInterrupts + "]");
		}
		else
		{

			try {
				ch1.increment();
			}
			catch (HardwareInterruptException hie){
				raisedInterrupts++;
				trace.info("  ++Channel 1 interrupt raised = " + raisedInterrupts + "]");
			}

			try {
				ch2.increment();
			}
			catch (HardwareInterruptException hie){
				raisedInterrupts++;
				trace.info("  ++Channel 2 interrupt raised = [" + raisedInterrupts + "]");
			}

			try {
				ch3.increment();
			}
			catch (HardwareInterruptException hie){
				raisedInterrupts++;
				trace.info("  ++Channel 3 interrupt raised = [" + raisedInterrupts + "]");
			}
		}

		try {
			PCB p = getCurrentProcess();
			if (p != null) {
				p.incrementTimeCount();			
			}
		}
		catch (HardwareInterruptException hie){
			raisedInterrupts++;
			trace.info("  ++Time interrupt raised = [" + raisedInterrupts + "]");
		}
		trace.info("  Raised interrupts = "+raisedInterrupts);
	}

	/**
	 * Called when control needs to be passed back to the OS
	 * though called masterMode this is more of and interrupt handler for the OS
	 * Interrupts processed in two groups TI = 0(CLEAR) and TI = 2(TIME_ERROR)
	 * @throws IOException
	 * @throws HardwareInterruptException 
	 * @throws CloneNotSupportedException 
	 */
	public void masterMode() throws IOException, HardwareInterruptException, CloneNotSupportedException {
		trace.finer("-->");
		/*
		 * This is handles a programming error i.e. bugs in setting Interrupts
		 */
		trace.info("Starting master mode");
		if (cpu.getPi().equals(Interrupt.WRONGTYPE)
				|| cpu.getTi().equals(Interrupt.WRONGTYPE)
				|| cpu.getSi().equals(Interrupt.WRONGTYPE)
				|| cpu.getIOi().equals(Interrupt.WRONGTYPE)){
			trace.info("  Wrong interrupt specified");
			return;
		}		

//		trace.finer("-->");
		trace.info("master mode: "+cpu.toString());

		if (!(cpu.getTi().equals(Interrupt.CLEAR)))
		{
			handleTimeInterrupt();
			raisedInterrupts--;
		}

		if (!(cpu.getPi().equals(Interrupt.CLEAR)))
		{
			handleProgramInterrupt(false);
			raisedInterrupts--;
		}

		if (!(cpu.getSi().equals(Interrupt.CLEAR)))
		{
			handleServiceInterrupt(false);
			raisedInterrupts--;
		}

		if (!(cpu.getIOi().equals(Interrupt.CLEAR)))
			handleIOInterrupt();

		assignChannel3();
		assignChannel2();
		trace.fine("End of Interrupt Handling");

		dispatch();
		trace.finer("<--");
	}

	private void handleIOInterrupt() throws HardwareInterruptException, IOException {
		trace.info(" Handle IOI interrupt: IOi = "+cpu.getIOi());

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

		trace.fine("Status "+cpu.dumpInterrupts());
	}

	private void handleProgramInterrupt(boolean timeError) {
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
				setError(p,Interrupt.OPERAND_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			case OPERATION_ERROR:
				setError(p,Interrupt.OPERATION_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			case PAGE_FAULT:
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
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
				setError(p,Interrupt.OPERAND_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			case OPERATION_ERROR:
				setError(p,Interrupt.OPERATION_ERROR.getErrorCode());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			case PAGE_FAULT:
				handlePageFault(p,true);
				break;
			}
		}

		if (!(cpu.getPi().equals(Interrupt.CLEAR)))
			cpu.setPi(Interrupt.CLEAR);

		if (p != null)
			trace.fine("  +++ PCB:" + p.getId() + " state = "+p.getState()+" next = "+p.getNextState());
	}

	private void handlePageFault(PCB p, boolean hasCPU) {
		trace.finer("-->");
		boolean valid = false;
		int pageNumber,frame,ptr;
		pageNumber = frame = ptr = 0;
		
		PCB pcb = p;
		if (pcb == null)
			pcb = getCurrentProcess();
		
		if (cpu.getStep() == CPUStep.FETCH) {
			//Page fault in fetch is always valid
			//get the page from the IC
			//Allocate a frame for the new instruction page
			//If we couldn't allocate a frame then swap with the LRU frame
			//write the instruction card from the current PCB to the newly allocated page
			trace.info("Page fault during fetch");
			pageNumber = cpu.getIc() / 10;
			frame = cpu.allocatePage(pageNumber);
			if (frame == 99) {
				trace.info("  Need to do some swapping");
				p.setState(ProcessStates.SWAP,ProcessStates.READY);
			}
			else
			{
				trace.fine("frame "+frame+" allocated for page "+pageNumber);
				//cpu.decrement();
			}
			p.setState(ProcessStates.IO_LOADINST,ProcessStates.READY);
			return;
		}



		String ir;

		if (hasCPU) { 
			ir = cpu.getIr();
			ptr = cpu.getPtr();
		} else {
			ir = pcb.getCPUState().getIr();
			ptr = pcb.getCPUState().getPtr();
		}

//		if (ir == null) {
//			trace.warning("ir=null");
//			return;
//		}
			

		valid = cpu.getMMU().validatePageFault(ptr,ir);


		if (pcb != null) {
			if (valid){
				if (hasCPU) {
					pageNumber = cpu.getOperand() / 10;
					frame = cpu.allocatePage(pageNumber); //TODO cleaner way to determine page #?
				} else {
					pageNumber = pcb.getCPUState().getOperand() / 10;
					frame = pcb.allocatePage(pageNumber); //TODO cleaner way to determine page #?
				}
				trace.info("Allocated a page to handle page fault");
//				trace.info("Allocated a page to handle page fault"+cpu.getMMU().getRam().toString());
				if (frame == 99) {
					trace.info("  Need to do some swapping");
					if (hasCPU) 
						pcb.setPageTable(cpu.getMMU().getPageTable());
					pcb.setState(ProcessStates.SWAP,ProcessStates.READY);
				}
				else
				{
					trace.fine("frame "+frame+" allocated for page "+pageNumber);
					//cpu.decrement();
				}
			} else {
				setError(pcb,ErrorMessages.INVALID_PAGE_FAULT.getErrCode());
				pcb.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
			}
		}
		trace.finer("<--");
	}

	private void handleServiceInterrupt(boolean timeError)
	{
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
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			case WRITE:
				//contextSwitch(ProcessQueues.IOQ);
				p.setTerminationStatus(ErrorMessages.TIME_LIMIT_EXCEEDED.getMessage());
				p.setState(ProcessStates.IO_WRITE,ProcessStates.TERMINATE);
				break;
			case TERMINATE:
				//	Dump memory
				trace.finer("\n"+cpu.dumpMemory());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
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
				p.setState(ProcessStates.IO_READ,ProcessStates.READY);
				break;
			case WRITE:
				trace.finest("Si interrupt write");
				p.setState(ProcessStates.IO_WRITE,ProcessStates.READY);
				break;
			case TERMINATE:
				//	Dump memory
				trace.fine("Case:Terminate");

				trace.fine("Memory contents: " + cpu.dumpMemory());
				p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
				break;
			}
		}

		if (!(cpu.getSi().equals(Interrupt.CLEAR)))
			cpu.setSi(Interrupt.CLEAR);

		if (p != null)
			trace.info("  +++ PCB:" + p.getId() + " state = "+p.getState()+" next = "+p.getNextState());

	}

	private void handleTimeInterrupt() throws IOException
	{
		KernelStatus retval = KernelStatus.CONTINUE;
		PCB p = getCurrentProcess();
		switch (cpu.getTi()) {

		case TIME_QUANTUM:
			trace.info("time quantum reached for "+p.getId());
			p.setState(ProcessStates.READY,ProcessStates.READY);
			break;
		case TIME_ERROR:
			handleServiceInterrupt(true);

			if (retval.equals(KernelStatus.CONTINUE))
				handleProgramInterrupt(true);

			/*
			 * Still have to handle a plain old Time Interrupt
			 */
			setError(p,Interrupt.TIME_ERROR.getErrorCode());
			p.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
			//System.exit(-1);
			break;
		}
		if (!(cpu.getTi().equals(Interrupt.CLEAR)))
			cpu.setTi(Interrupt.CLEAR);

		if (p != null)
			trace.info("  +++ PCB:" + p.getId() + " state = "+p.getState()+" next = "+p.getNextState());

	}

	/**
	 * Process the channel 3 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void processCh3() throws HardwareInterruptException {
//		trace.finest("-->");
		//process the current task
		//Switch over the possible tasks
		ChannelTask task = ch3.getTask();
		trace.info("  Processing Channel 3, "+task);
		switch (task.getType()) {
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
			swapInTask();
			break;
		case SWAP_OUT:
			swapOutTask();
			break;
		default:
			trace.severe("Unknown task");
		}

		dumpBuffers();
		//		//Decrement IOi by 4 to signal channel handled
		//		trace.finer("Decrementing IOi by 4");
		cpu.clearIOi(Interrupt.IO_CHANNEL_3.getValue());

		trace.finest("<--");
	}

	private void swapOutTask() {
		PCB swapPCB = swapQueue.remove();
		PageTable pt = swapPCB.getPageTable();

		int drumTrack = ch3.getTask().getTrack();
		int victimPage = (Integer) ch3.getTask().getMisc();

		// write drum track to PTE
		pt.getEntry(victimPage).setBlockNum(drumTrack);
		// clear dirty bit in PTE
		pt.getEntry(victimPage).setDirty(false);
		pt.getEntry(victimPage).setSwap();
		swapPCB.setPageTable(pt);
		swapPCB.setState(null,ProcessStates.SWAP_IN);
		schedule(swapPCB);
	}

	private void swapInTask() {
		PCB swapPCB = swapQueue.remove();
		PageTable pt = swapPCB.getPageTable();

		int newPage = (Integer) ch3.getTask().getMisc();
		int victimFrame = ch3.getTask().getFrame();

		// clear swapped/dirty bits in PTE
		pt.getEntry(newPage).setDirty(false);
		pt.getEntry(newPage).setSwap(false);
		// write frame num to PTE
		pt.getEntry(newPage).setBlockNum(victimFrame);
		swapPCB.setPageTable(pt);
		swapPCB.setState(null,ProcessStates.READY);
		schedule(swapPCB);
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
//		trace.finer("-->");

		PCB ioPCB = ioQueue.remove();
		trace.info("  CPU State: " + ioPCB.getCPUState().toString());
		ioPCB.setState(ProcessStates.READY, ProcessStates.READY);
		schedule(ioPCB);

//		trace.finer(retval+"<--");
		return retval;
	}

	/**
	 * Processing of a write from the PD instruction
	 * @return
	 */
	private KernelStatus putDataTask(){
		KernelStatus retval = KernelStatus.CONTINUE;
//		trace.finer("-->");

		PCB ioPCB = ioQueue.remove();
		trace.info("  CPU State: " + ioPCB.getCPUState().toString());
		ioPCB.setState(ProcessStates.READY, ProcessStates.READY);
		schedule(ioPCB);

//		trace.finer(retval+"<--");
		return retval;
	}

	private void outputSpoolingTask() throws HardwareInterruptException {
		//release track
		cpu.getMMU().getDrum().freeTrack(ch3.getTask().getTrack());
		//Get the PCB being output spooled.
		PCB p = terminateQueue.peek();
		trace.info("  Output spooling " + p.getId());
		trace.fine(ch3.getTask().getBuffer().toString());

		trace.fine(ch3.getTask().toString());

		trace.fine(p.getNextDataTrack()+"");
		
		p.removeOutputTrack();
		putOutputFullBuffer(ch3.getTask().getBuffer());
		
		//Check if output spooling is complete.
//		if (p.outputComplete()) {
//			trace.fine("Output Spooling complete for pid:"+p.getId());
//			Buffer b = getEmptyBuffer();
//			if (b != null) {
//				//Prepare blank lines for print in between program output
//				b.setData("-\n-");					
//				b.setOutputFull();
//				putOutputFullBuffer(b);
//				dumpBuffers();
//			}
//			else {
//				trace.warning("no empty buffers!");
//				//TODO Need to handle this somehow. Otherwise these lines done get outputted.
//			}
//		}	
//		else {
//			putOutputFullBuffer(ch3.getTask().getBuffer());
//		}
	}

	private void inputSpoolingTask() {
		trace.info("  +++Adding track"+ch3.getTask().getTrack());
		if (inputPCB.isProgramCardsToFollow()) {
			trace.info("    +++adding instruction track");
			inputPCB.addInstructionTrack(ch3.getTask().getTrack());
		}
		else {
			trace.info("    +++adding data track");
			inputPCB.addDataTrack(ch3.getTask().getTrack());
		}
		ch3.getTask().getBuffer().setEmpty();	
		putEmptyBuffer(ch3.getTask().getBuffer());
	}

	/**
	 * Assign the next task to ch3
	 * @throws HardwareInterruptException 
	 * @throws IOException 
	 */
	private void assignChannel3 () throws HardwareInterruptException, IOException {
//		trace.finest("-->");

		if (ch3.isBusy()) {
			trace.info("  Channel 3 is busy");
			return;
		}
		ChannelTask task = new ChannelTask();

		if (!ifbq.isEmpty()) {
			trace.info("  Assign a input spooling task to channel 3");
			Buffer b = getInputFullBuffer();
			trace.fine(""+b.toString());
			int track = cpu.getMMU().allocateTrack();

			task.setBuffer(b);
			task.setType(ChannelTask.TaskType.INPUT_SPOOLING);
			task.setTrack(track);
			trace.info("ch3:" + task.getType().toString());
		}

		else if (swapQueue.size() > 0) {
			PCB swapPCB = swapQueue.peek();
			trace.info("  "+swapPCB.getId() + ": assign a swap task to channel 3");
			PageTable pt = swapPCB.getPageTable();
			//trace.info(pt.toString());
			int newPage = swapPCB.getCPUState().getOperand() / 10;
			// Find victim page: LRU[3]
			int victimPage = pt.getVictim();
			//trace.info("victimPage = " + victimPage);
			// Find victim frame to swap out
			int victimFrame = pt.getEntry(victimPage).getBlockNum();
			// if dirty bit for victim page is on, call proc SwapOut to swap out victim
			if (pt.getEntry(victimPage).isDirty()) {
				// Get a free drum track
				Random generator = new Random();
				Drum drum = cpu.getMMU().getDrum();
				// assignSwapOut(task,pt,victimPage,victimFrame);
				int drumTrack = drum.getFreeTracks().get(generator.nextInt(drum.getFreeTracks().size()));
				// write victim page to drum track -- this will get queued up on the swap queue
				task.setType(TaskType.SWAP_OUT);
				task.setTrack(drumTrack);
				task.setFrame(victimFrame);
				task.setMisc(victimPage);
			}
			else
			{
				pt.getEntry(victimPage).setSwap();
				// call proc SwapIn to swap in the new page
				if (pt.getEntry(newPage).isSwapped()) {
					task.setType(TaskType.SWAP_IN);
					task.setFrame(victimFrame);
					task.setMisc(newPage);				
					task.setTrack(pt.getEntry(newPage).getBlockNum());
				}
				else {
					swapPCB = swapQueue.remove();
					// clear swapped/dirty bits in PTE
					pt.getEntry(newPage).setDirty(false);
					pt.getEntry(newPage).setSwap(false);
					// write frame num to PTE
					pt.getEntry(newPage).setBlockNum(victimFrame);
					swapPCB.setPageTable(pt);
					swapPCB.setState(ProcessStates.READY, null);
					schedule(swapPCB);
				}
			}
		}
		else if (terminateQueue.size() > 0 &&							//if there's a process that needs to be terminated AND						
				(terminateQueue.peek().getHeaderLinedPrinted() != 2 ||	//both header lines have not been printed OR 
				terminateQueue.peek().outputComplete() == false)) {		//there's still some output for the terminate process that can be spooled
																		//then assign the header or output line to channel 
			Buffer eb = getEmptyBuffer();
			if (eb != null) {
				PCB termPCB = terminateQueue.peek();
				if (termPCB.getHeaderLinedPrinted() == 0) {
					trace.info("  "+termPCB.getId()+": assign header line 1 to channel 3");
					eb.setData("-\n-\n"+termPCB.getId()+" "+termPCB.getTerminationStatus());
					termPCB.incrementHeaderLinedPrinted();
					eb.setOutputFull();
					putOutputFullBuffer(eb);
				}
				else if (termPCB.getHeaderLinedPrinted() == 1) {
					trace.info(termPCB.getId()+": assign header line 2 to channel 3");
					eb.setData(termPCB.getCPUState().getState()+"    "+termPCB.getCurrentTime()+"    "+termPCB.getLines()+"\n-");
					termPCB.incrementHeaderLinedPrinted();
					eb.setOutputFull();
					putOutputFullBuffer(eb);
				}
				else {
					int track = termPCB.getNextOutputTrack();
					//Set the track to read from
					if (track >= 0) {
						trace.info(termPCB.getId()+": assign output " + track + " to channel 3");
						task.setType(ChannelTask.TaskType.OUTPUT_SPOOLING);
						task.setTrack(track);
						task.setBuffer(eb);
					}
				}
				trace.info(termPCB.toString());
			}
		}
		else if (ioQueue.size() > 0) {
			PCB ioPCB = ioQueue.peek();
			trace.info(ioPCB.getId()+": assign a IO task to channel 3");
			int irValue = 0;
			int track = 0;
			int frame = 0;
			if (ioPCB.getState().equals(ProcessStates.IO_LOADINST.getName())) {
				trace.info("Assign a load instruction card to channel 3");
				irValue = ioPCB.getCPUState().getOperand();
				cpu.setPi(irValue);
				//if the state is IO_LOADINST then we need to copy an instruction card from the drum to 
				//RAM because of pure demand paging.  
				//the track we need to copy from is stored in the PCB in the instruction cards
//				irValue = ioPCB.getCPUState().getOperand();
				track = ioPCB.getNextInstruction(ioPCB.getCPUState().getIc());
				trace.fine("Instruction track for "+ioPCB.getCPUState().getIc()+": "+track);
				frame = cpu.getMMU().getFrame(ioPCB.getCPUState().getPtr(),ioPCB.getCPUState().getIc());
				trace.fine("Frame for "+ioPCB.getCPUState().getIc()+": "+frame);
				task.setTrack(track);
				task.setFrame(frame);
				task.setType(TaskType.GD);
			}
			else if (ioPCB.getState().equals(ProcessStates.IO_READ.getName()))
			{
				irValue = ioPCB.getCPUState().getOperand();
				cpu.setPi(irValue);
				track = ioPCB.getNextDataTrack();

				trace.fine("next data track:"+track);
				
				try {
					frame = cpu.getMMU().getFrame(ioPCB.getCPUState().getPtr(),irValue);
				}
				catch(HardwareInterruptException hie) {
					if ((cpu.getPi().equals(Interrupt.PAGE_FAULT)))
					{
						trace.info("Allocate frame before starting Channel 3");
						handlePageFault(ioPCB,ioPCB.isRunning());
						cpu.setPi(Interrupt.CLEAR);
					}
					else {
						trace.warning("We are not supposed to be here");
					}
				}
				finally {
					frame = cpu.getMMU().getFrame(ioPCB.getCPUState().getPtr(),irValue);
					//trace.info("using frame " + frame);
				}

				if (track <= 0) {
					trace.info("No data tracks remaining for pid "+ioPCB.getId());
					setError(ioPCB,ErrorMessages.OUT_OF_DATA.getErrCode());
					ioPCB.setState(null,ProcessStates.TERMINATE);
					//throw new RuntimeException("BREAK");
				}
				else
				{
					if (cpu.getPi() == Interrupt.CLEAR) {
						task.setTrack(track);
						task.setFrame(frame);
						task.setType(TaskType.GD);
					} else {
						setError(ioPCB,ErrorMessages.OPERAND_FAULT.getErrCode());
						ioPCB.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
					}			
				}
			}
			else if (ioPCB.getState().equals(ProcessStates.IO_WRITE.getName()))
			{
				// Increment the line limit counter
				if (!ioPCB.incrementPrintCount()){
					setError(ioPCB,ErrorMessages.LINE_LIMIT_EXCEEDED.getErrCode());
					ioPCB.setState(null,ProcessStates.TERMINATE);
				} else {
					// get memory location and set exception if one exists
					irValue = ioPCB.getCPUState().getOperand();

					try {
						frame = cpu.getMMU().getFrame(ioPCB.getCPUState().getPtr(),irValue);
					}
					catch(HardwareInterruptException hie) {
						if ((cpu.getPi().equals(Interrupt.PAGE_FAULT)))
						{
							trace.info("allocate frame before starting Channel 3");
							handlePageFault(ioPCB,ioPCB.isRunning());
							cpu.setPi(Interrupt.CLEAR);
						}
						else {
							trace.info("We are not supposed to be here");
						}
					}
					finally {
						frame = cpu.getMMU().getFrame(ioPCB.getCPUState().getPtr(),irValue);
					}

					cpu.setPi(irValue);
					if (cpu.getPi() == Interrupt.CLEAR) {
						track = cpu.getMMU().allocateTrack();
						
						ioPCB.bufferOutputLine(track);
						task.setFrame(frame);
						task.setTrack(track);
						task.setType(ChannelTask.TaskType.PD);
					} else {
						setError(ioPCB,ErrorMessages.OPERAND_FAULT.getErrCode());
						ioPCB.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
					}			
				}
			}
		}
		else {
			trace.info("  Nothing to do for channel 3");
		}

		//If a task type was set, start the channel
		if (task.getType() != null) {
			try {
				ch3.start(task);
			} catch (HardwareInterruptException e) {
				trace.severe("Tried to start a task on a channel that was busy");
				e.printStackTrace();
			}
		}
		if (getCurrentProcess() != null)
			trace.info("+++ PCB:" + getCurrentProcess().getId() + " state = "+getCurrentProcess().getState()+" next = "+getCurrentProcess().getNextState());
		trace.finest("<--");
	}

	private void assignChannel2() throws HardwareInterruptException {
		if (ch2.isBusy()) {
			trace.info("  Channel 2 is busy");
			return;
		}
		// start another task for channel 2
		Buffer ofb = getOutputFullBuffer();
		if (ofb != null) {
			trace.info("  Assign output to channel 2");
			trace.fine(ofb.toString());
			ChannelTask task = new ChannelTask();
			task.setBuffer(ofb);
			task.setType(TaskType.OUTPUT_SPOOLING);
			task.setFrame(terminateQueue.peek().getCurrOutputTrack());
			ch2.start(task);
		}
		else {
			trace.warning("  Nothing to do for channel 2");
		}

	}

	/**
	 * Process the ch1 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void processCh1() throws HardwareInterruptException {
//		trace.finer("-->");
		trace.info("  Processing channel 1");
		trace.info("Processing channel 1 interrupt");
		//Get the ifb from the current task
		ChannelTask lastTask = ch1.getTask();		//BJD We need to ensure we get the buffer that was associated to the latest task

		if (lastTask != null) {
			Buffer b = lastTask.getBuffer();
			String data = b.getData();
			//If its a control card, process accordingly
			if (data.startsWith(PCB.JOB_START)) {
				processAMJ(lastTask.getBuffer());
			}
			else if (data.startsWith(PCB.DATA_START)) {
				processDTA(lastTask.getBuffer());
			}
			else if (data.startsWith(PCB.JOB_END)) {
				processEOJ(lastTask.getBuffer());
			}
			else {
				trace.finer(data+" is not a control card, adding to ifbq:"+b);
				putInputFullBuffer(b);
				trace.fine(ifbq.toString());
				
//				if (inputPCB.isProgramCardsToFollow()) {
//					inputPCB.incrementExpectedInstructionTracks();
//				}
//				else {
//					inputPCB.incrementExpectedDataTracks();
//				}
				
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
		cpu.clearIOi(Interrupt.IO_CHANNEL_1.getValue());
//		trace.finer("<--");
	}

	/**
	 * Process the ch2 interrupt
	 * @throws HardwareInterruptException 
	 */
	private void processCh2() throws HardwareInterruptException {
//		trace.finer("-->");
		trace.info("  Processing channel 2");

		putEmptyBuffer(ch2.getTask().getBuffer());
		
		//start another task for channel 2
		Buffer ofb = getOutputFullBuffer();
		if (ofb != null) {
			ChannelTask task = new ChannelTask();
			task.setBuffer(ofb);
			task.setType(TaskType.OUTPUT_SPOOLING);
			task.setFrame(terminateQueue.peek().getCurrOutputTrack());
			ch2.start(task);
		}
		else {
			trace.warning("Nothing to do for channel 2!");
			PCB terminatedPCB = terminateQueue.peek();
			if (terminatedPCB.headerLinedPrinted == 2 
					&& terminatedPCB.outputComplete()) {
				terminatedPCB = terminateQueue.remove();

				//Terminate the process
				terminatedPCB.terminate();
				processCount--;
			}
		}

		//		//Decrement IOi by 2 to signal channel 2 handled
		//		trace.finer("Decrementing IOi by 2");
		cpu.clearIOi(Interrupt.IO_CHANNEL_2.getValue());
		trace.finer("<--");
	}

	/**
	 * Parse the job card from an ifb, create the PCB.
	 */
	private void processAMJ(Buffer b) {

		String jobCard = b.getData();
		trace.info("  Loading job:"+jobCard);

		//Parse Job Data
		String id = jobCard.substring(4, 8);
		int maxTime = Integer.parseInt(jobCard.substring(8, 12));
		int maxPrints = Integer.parseInt(jobCard.substring(12, 16));

		//Create PCB
		inputPCB = new PCB(id, maxTime, maxPrints);
		inputPCB.setState(ProcessStates.SPOOL,ProcessStates.READY);

		//Return buffer to ebq
		b.setEmpty();
		putEmptyBuffer(b);

	}

	private void processDTA(Buffer b) {

		trace.info("$DTA for "+inputPCB.getId());
		inputPCB.setProgramCardsToFollow(false);
		b.setEmpty();
		putEmptyBuffer(b);

	}

	private void processEOJ(Buffer b) throws HardwareInterruptException {

		String eojCard = b.getData();
		String id = eojCard.substring(4, 8);
		//trace.info("Finished spooling in job "+eojCard);

		//trace.info("***\n"+cpu.getMMU().toString());
		//trace.info("***"+inputPCB.toString());

		//Once the EOJ is reached, move the PCB to the ready queue.
		trace.info(eojCard+" encountered");
		
		//if (inputPCB.isReady()) {
			trace.info("Placing process "+id+" on readyQueue");
			trace.fine("Placing process readyQueue:"+inputPCB.toString());
			readyQueue.add(inputPCB);
			inputPCB = null;
			processCount++;
//		}
//		else {
//			trace.info("spooling still in process, cannot");
//			spoolQueue.add(inputPCB);
//		}
		//Return buffer to ebq
		b.setEmpty();
		putEmptyBuffer(b);

	}

	/**
	 * Swap in copied from MMU
	 * @param newPage
	 * @param frame
	 */
	public void assignSwapIn(ChannelTask task,PageTable pt, int newPage, int frame) {
		// write new page to specified frame if it's on the drum -- this will get queued on the swap queue
		if (pt.getEntry(newPage).isSwapped()) {
			task.setType(TaskType.SWAP_IN);
			task.setFrame(frame);
			task.setTrack(pt.getEntry(newPage).getBlockNum());
		}
		// clear swapped/dirty bits in PTE
		pt.getEntry(newPage).setDirty(false);
		pt.getEntry(newPage).setSwap(false);
		// write frame num to PTE
		pt.getEntry(newPage).setBlockNum(frame);
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

//		trace.finer("-->");

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

//		trace.finer(retval+"<--");
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
//		trace.finer("-->");
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
//		trace.finer("<--");
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
	public void setError(PCB pcb,int err) {
//		trace.finer("-->");
		trace.fine("setting err="+err);
		errMsg = ErrorMessages.set(err);
		PCB p = getCurrentProcess();

		if (pcb != null)
			p = pcb;

		if (!p.getErrorInProcess()){
			p.setErrorInProcess();
			p.setTerminationStatus(errMsg.getMessage());	
		} else {
			p.appendTerminationStatus(errMsg.getMessage());	
		}
		trace.info("termination status="+p.getTerminationStatus());
//		trace.finer("<--");
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
		if (ebq.isEmpty()) {
			return null;
		}
		else {
			Buffer eb = ebq.remove();
			trace.fine(eb.toString());
			return eb; 
		}
		//return getBufferOfState(BufferState.EMPTY);
	}	

	/**
	 * Gets the next input-full buffer
	 * @return An input-full buffer, null if no input-full buffers exist
	 */
	private Buffer getInputFullBuffer() {
		if (ifbq.isEmpty()) {
			return null;
		}
		else {
			Buffer ifb = ifbq.remove();
			trace.fine(ifb.toString());
			return ifb; 

		}
		//return getBufferOfState(BufferState.INPUT_FULL);
	}

	/**
	 * Gets the next output full buffer
	 * @return An output-full buffer, null if no output-full buffers exist
	 */
	private Buffer getOutputFullBuffer() {
		if (ofbq.isEmpty()) {
			return null;
		}
		else {
			Buffer ofb = ofbq.remove();
			trace.fine(ofb.toString());
			return ofb; 
		}
		//return getBufferOfState(BufferState.OUTPUT_FULL);
	}

	private void putEmptyBuffer(Buffer b) {
		trace.fine("adding eb"+b);
		ebq.add(b);
	}
	private void putInputFullBuffer(Buffer b) {
		trace.fine("adding ifb"+b);
		ifbq.add(b);
	}

	private void putOutputFullBuffer(Buffer b) {
		trace.fine("adding ofb"+b);
		ofbq.add(b);
	}

	/**
	 * Gets the next of the given state
	 * @return The next buffer of the given state, null if none exist
	 */
//	private Buffer getBufferOfState(BufferState state) {
////		trace.finest("-->");
//		dumpBuffers();
//		//trace.finer(""+buffers);
//		//trace.fine(buffers.size()+" buffers");
//		Buffer returnBuffer = null;
//		for (int i = 0; i< buffers.size(); i++) {
//			Buffer b = buffers.get(i);
//			if (b.getState().getCurrent().equals(state.getStateName()) && !b.isLocked()) {
//				returnBuffer = b;
//			}
//		}
//		if (returnBuffer != null)
//		  trace.fine("returning "+state.getStateName()+" buffer:"+returnBuffer);
////		trace.finest("<--");
//		return returnBuffer;
//	}
	
	private void dumpBuffers() {
		trace.fine("ebq:"+ebq.size());
		for (Buffer b : ebq) {
			trace.fine(b.toString());
		}
		trace.fine("ifbq:"+ifbq.size());
		for (Buffer b : ifbq) {
			trace.fine(b.toString());
		}
		trace.fine("ofbq:"+ofbq.size());
		for (Buffer b : ofbq) {
			trace.fine(b.toString());
		}
//		for (int i = 0; i< buffers.size(); i++) {
//			Buffer b = buffers.get(i);
//			trace.fine(b.toString());
//		}
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
			pcb.setState(ProcessStates.TERMINATE,ProcessStates.TERMINATE);
			terminateQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.MEMORY.getName())) {
			pcb.setState(ProcessStates.MEMORY,ProcessStates.READY);
			memoryQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.IO_READ.getName())
				|| pcb.getNextState().equals(ProcessStates.IO_WRITE.getName())) {

			if (pcb.getNextState().equals(ProcessStates.IO_READ.getName()))
				pcb.setState(ProcessStates.IO_READ,ProcessStates.READY);
			else
				pcb.setState(ProcessStates.IO_WRITE,ProcessStates.READY);

			ioQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.SWAP.getName())) {
			pcb.setState(ProcessStates.SWAP,ProcessStates.READY);
			swapQueue.add(pcb);
		}
		else if(pcb.getNextState().equals(ProcessStates.SWAP_IN.getName())) {
			pcb.setState(ProcessStates.SWAP_IN,ProcessStates.READY);
			swapQueue.add(pcb);
		}		
		else if(pcb.getNextState().equals(ProcessStates.READY.getName())) {
			pcb.setState(ProcessStates.READY,ProcessStates.READY);
			readyQueue.add(pcb);
		}
	}

	private void contextSwitch(ProcessQueues targetQ) throws IOException, HardwareInterruptException, CloneNotSupportedException {

		
		// Switches currently running process from head of ready queue to tail of the target queue
		PCB movePCB = readyQueue.remove();
		// Stores CPU status to the PCB that is being switched
		try {
			if (movePCB.getCPUState() == null || readyQueue.size() != 0) {
				movePCB.setCpuState((CPUState) cpu.getCPUState().clone());
				movePCB.setPageTable(cpu.getMMU().getPageTable());
			}

			trace.info("+++ out > " + movePCB.getId() + ":" + movePCB.getState() + ":" + movePCB.getCPUState().toString());
			trace.info("+++ out pcb> " + movePCB.getPageTable().toString());
			trace.info("+++ out cpu> " + cpu.getMMU().getPageTable().toString());
			//trace.info(cpu.getMMU().getRam().toString());
			movePCB.setRunning(false);
		} catch (CloneNotSupportedException e) {
			trace.log(Level.WARNING, "Failed to clone current CPU state", e);
		}
		switch (targetQ) {
		case READYQ: 
			movePCB.resetCurrentQuantum();
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
			trace.info("Swap to SwapQ. ReadyQ now contains "+readyQueue.size()+" processes. SwapQ contains "+swapQueue.size()+" processes");
			trace.fine("Swap to SwapQ. ReadyQ now contains "+readyQueue.size()+" processes. SwapQ contains "+swapQueue.size()+" processes");
			break;
		case TERMINATEQ:
			terminateQueue.add(movePCB);
			trace.finer("To TerminateQ: "+movePCB.toString());
			trace.info("Swap to TerminateQ. ReadyQ now contains "+readyQueue.size()+" processes. TerminateQ contains "+terminateQueue.size()+" processes");
			trace.fine("Swap to TerminateQ. ReadyQ now contains "+readyQueue.size()+" processes. TerminateQ contains "+terminateQueue.size()+" processes");

			break;
		}


		// Load CPU status from new head of ready queue to CPU
		if (readyQueue.size() >= 1) {
			PCB currentPCB = getCurrentProcess();
			
			trace.info("+++ in < " + currentPCB.getId() + ":" + currentPCB.getState() + ":" + cpu.getCPUState().toString());
			if (currentPCB.getState().equals(ProcessStates.SPOOL.getName()))
				initialProcessLoad();
			else {
				trace.fine(cpu.getState().toString());
				cpu.setState(currentPCB.getCPUState());
//				currentPCB.getPageTable().storePageTable();
//				trace.info("+++ in < " + currentPCB.getPageTable().toString());	//AMC: I commented this out.  Storing a copy of the page table in the PCB is
				//unnecessary. The PTR is stored in the CPU State in the PCB and the page table can be read from RAM using that.  Storing a copy of the page
				//table was causing so many disjoint copies floating around I kept introducing new errors :(
			}
			currentPCB.setRunning(true);
			//trace.info("*** " + getCurrentProcess().getPageTable().toString());
		}
		else
		{
			dummyPCB.getCPUState().setIc(0);
			cpu.setState(dummyPCB.getCPUState());
			dummyPCB.getPageTable().storePageTable();
			trace.info("+++< " + dummyPCB.getId());
			trace.info("+++< " + dummyPCB.getCPUState().toString());
		}
	}

	private void dispatch() throws HardwareInterruptException, CloneNotSupportedException, IOException {
		PCB pcb = getCurrentProcess();
		
		if (pcb != null) {
			trace.info("dispatch:"+pcb.getId()+ ":" +pcb.getState() + ":" + pcb.getNextState());
			//if (pcb.getCPUState() != null)
			//trace.info("+++ "+pcb.getCPUState().toString());
			if(pcb.getState().equals(ProcessStates.TERMINATE.getName())) {
				contextSwitch(ProcessQueues.TERMINATEQ);
				//System.exit(-1);
			}
			else if(pcb.getState().equals(ProcessStates.MEMORY.getName())) {
				contextSwitch(ProcessQueues.MEMORYQ);
			}
			else if(pcb.getState().equals(ProcessStates.IO_READ.getName())
					|| pcb.getState().equals(ProcessStates.IO_WRITE.getName())
					|| pcb.getState().equals(ProcessStates.IO_LOADINST.getName())) {
				contextSwitch(ProcessQueues.IOQ);
			}
			else if(pcb.getState().equals(ProcessStates.SWAP.getName())) {
				contextSwitch(ProcessQueues.SWAPQ);
			}
			else if(pcb.getState().equals(ProcessStates.READY.getName())) {
				contextSwitch(ProcessQueues.READYQ);
			}
			else if(pcb.getState().equals(ProcessStates.SPOOL.getName())) {
				initialProcessLoad();
			}
		}
	}

	private void initialProcessLoad() throws HardwareInterruptException, CloneNotSupportedException, IOException {
		trace.finer("-->");
		PCB pcb = getCurrentProcess();
		pcb.setState(ProcessStates.READY,ProcessStates.READY);
		trace.info(pcb.getId());
		if (readyQueue.size() == 1) {
			inputSpoolingComplete = true;
		}
		//trace.info("initialize PCB: " + pcb.getId());
		trace.fine("***\n"+pcb.toString());
		//Create a CPU state;
		CPUState pcbCPUState = new CPUState();
		cpu.setState(pcbCPUState);
		cpu.setIr("0000");
		cpu.initPageTable();

//		// load a page of instructions into memory
//		int frame = cpu.allocatePage(0);
//		int instruction = pcb.getNextInstruction(0);
//		String program = cpu.getMMU().getDrum().read(cpu.getPtr(),instruction);
//		cpu.getMMU().writeFrame(cpu.getPtr(),frame, program);
		pcb.startExecution();

		//trace.info(cpu.getMMU().getRam().toString());
		//trace.info("***\n"+cpu.getCPUState().toString());
		trace.finer("<--");
	}
}
