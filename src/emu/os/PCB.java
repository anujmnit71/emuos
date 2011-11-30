package emu.os;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import emu.hw.CPU;
import emu.hw.CPUState;
import emu.hw.MMU;
import emu.hw.PageTable;
import emu.hw.CPUState.Interrupt;
import emu.hw.HardwareInterruptException;

/**
 * Process Control Block
 * @author b.j.drew@gmail.com
 *
 */
public class PCB {
	/**
	 * Tracer
	 */
	static Logger trace = Logger.getLogger("emuos");
	public static final String JOB_START = "$AMJ";
	public static final String DATA_START = "$DTA";
	public static final String JOB_END = "$EOJ";
	/**
	 * Process ID 
	 */
	String id;
	/**
	 * Max number of time units of execution
	 */
	int maxTime;
	/**
	 * Max number of prints
	 */
	int maxPrints;
	/**
	 * Current number of total time cycles this process has been running
	 */
	int currentTime;
	/**
	 * Current number of cycles since last context switch
	 */
	int currentQuantum;
	/**
	 * Maximun number of cycles before context switch
	 */
	int maxQuantum = 10;
	/**
	 * Current number of prints
	 */
	int currPrints;
	/**
	 * was there an error in this process
	 */
	boolean errorInProcess;
	/**
	 * Message describing how the process terminated
	 */
	String terminationStatus;
	/**
	 * 
	 */
	boolean running;
	/**
	 * The saved CPU state after context switch
	 */
	CPUState cpuState;
	/**
	 * Tracks containing output to be spooled to the printer
	 */
	private Queue<Integer> outputTracks;
	/**
	 * Tracks containing programs instructions
	 */
	private List<Integer> instructionTracks;
	/**
	 * Tracks containing program data
	 */
	private Queue<Integer> dataTracks;
	/**
	 * The process state
	 */
	State state;
	/**
	 * PageTable
	 */
	private PageTable pageTable;
	/**
	 * 
	 */
	protected final int pagesAllowedInMemory = 3; /* 4 are allowed but one will always be the page table */
	/**
	 * Set true if program cards need to be spooled, 
	 * otherwise it is assumed data cards are to follow
	 */
	boolean programCardsToFollow = true;
	/**
	 * The number of header lines that have output spooled. These are the lines that precede the programs outout.
	 */
	int headerLinedPrinted = 0;

	/**
	 * Control Flags for processing PCBs
	 * READY Process is ready to execute
	 * EXECUTE Process is executing
	 * IO  Process is waiting for IO
	 * MEMORY Process is waiting for memory
	 * TERMINATE Process is terminating
	 * SWAP Process is swapping memory
	 * SPOOL Process is being spooled 
	 */
	public enum ProcessStates {
		READY     ("ready"),
		EXECUTE   ("execute"),
		IO_READ   ("io-read"),
		IO_WRITE  ("io-write"),
		MEMORY    ("memory"),
		TERMINATE ("terminate"),
		SWAP      ("swap"),
		SWAP_IN   ("swap-in"),
		SPOOL     ("spool");

		String name;
		ProcessStates(String name) {
			this.name = name;
		}
		public String getName() {
			return name;
		}
	}

	/**
	 * 
	 * @param id
	 * @param maxTime
	 * @param maxPrints
	 */
	public PCB(String id, int maxTime, int maxPrints) {
		trace.info("id="+id+", maxTime="+maxTime+", maxPrints="+maxPrints);
		this.id = id;
		this.maxTime = maxTime;
		this.maxPrints = maxPrints;
		this.outputTracks = new LinkedList<Integer>();
		this.instructionTracks = new ArrayList<Integer>();
		this.dataTracks = new LinkedList<Integer>();
		this.state = new State();
		state.setCurrent(ProcessStates.SPOOL.getName());
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getMaxTime() {
		return maxTime;
	}

	public int setMaxTime(int maxTime) {
		this.maxTime = maxTime;
		return maxTime;
	}

	public int getMaxPrints() {
		return maxPrints;
	}

	public int setMaxPrints(int maxPrints) {
		this.maxPrints = maxPrints;
		return maxPrints;
	}

	public int getCurrentTime() {
		return currentTime;
	}
	public int getCurrentPrints() {
		return outputTracks.size();
	}
	public int bufferOutputLine(int track) {
		outputTracks.add(track);
		return outputTracks.size();
	}

	public void addInstructionTrack(int track) {
		instructionTracks.add(track);
	}

	/**
	 * @param ic
	 * @return the track containing the instruction
	 */
	public int getNextInstruction(int ic) {
		int retval = -1;
		//WM not sure about this
		if (getNumInstructionTracks() > 0) {
			int value = ic % getNumInstructionTracks();
			retval = instructionTracks.get(value);
		}
		return retval;
	}

	public void addDataTrack(int track) {
		dataTracks.add(track);
	}

	public int getNextDataTrack() {
		int retval = -1;
		if (getNumDataTracks() > 0)
			retval = dataTracks.remove();
		return retval;
	}

	public int getNumInstructionTracks() {
		return instructionTracks.size();
	}

	public int getNumDataTracks() {

		return dataTracks.size();
	}
	/**
	 * Called after program load
	 * @throws IOException 
	 */
	public void startExecution() throws IOException {
		trace.fine("-->");
		trace.info("starting process "+id);
		running = true;
		Kernel.getInstance().getCpu().setIc(0);
		Kernel.getInstance().getCpu().setSi(Interrupt.CLEAR);
		setTerminationStatus("Normal Execution");
		trace.fine("<--");
	}

	/**
	 * Increment time count and throw exception if max time limit is exceeded
	 * @throws HardwareInterruptException if there is an error
	 */
	public void incrementTimeCount() throws HardwareInterruptException {
		if (!incrementTime()) {
			throw new HardwareInterruptException();
		}
	}

	/**
	 * Increment time count and throw exception if max time limit is exceeded
	 * return false if there is an error
	 */
	public boolean incrementTimeCountMaster()  {
		return incrementTime();
	}

	/**
	 * 
	 * @return
	 */
	private boolean incrementTime() {
		boolean retval = true;
		currentTime++;
		currentQuantum++;

		if (currentQuantum <= maxQuantum) {
			trace.fine("pid: "+id+", currentQuantum: "+currentQuantum);
		}
		else {
			trace.info("quantum reached for "+id);
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_QUANTUM);
			retval = false;
		}

		if (currentTime <= maxTime) {
			trace.fine("curr time: "+currentTime+", max time="+maxTime);
		} else {
			if (maxTime >= 0) {
				trace.severe("max time ("+maxTime+") exceeded");
				Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
				retval = false;
			}
		}
		return retval;
	}

	/**
	 * Increment print count and throw exception if max print limit is exceeded
	 * This will only be called from masterMode so an exception is not necessary
	 */
	public boolean incrementPrintCount() {

		currPrints++;

		if (currPrints <= maxPrints) {
			return true;
		} 
		trace.severe("max prints ("+maxPrints+") exceeded");
		return false;
	}

	/**
	 * get the number of printed lines of a process
	 * @return
	 */
	public int getLines() {
		return currPrints;		
	}

	/**
	 * Clear exiting message
	 * @param msg
	 */
	public void setTerminationStatus(String msg) {
		terminationStatus = msg;
		trace.finer(""+terminationStatus);
	}

	/**
	 * append incoming message to existing message
	 * @param msg
	 */
	public void appendTerminationStatus(String msg) {
		terminationStatus += ", " + msg;
		trace.finer(""+terminationStatus);
	}

	/**
	 * Return the termination status of process
	 * @return
	 */
	public String getTerminationStatus() {
		trace.finer(""+terminationStatus);
		return terminationStatus;		
	}

	/**
	 * flag to know that an error message will be displayed
	 */
	public void setErrorInProcess(){
		errorInProcess = true;
		trace.finer("<-->");
	}

	/**
	 * return error status of process
	 * @return
	 */
	public boolean getErrorInProcess(){
		trace.finer("getErrorInProcess(): "+errorInProcess);
		return errorInProcess;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}
	/**
	 * Free resources allocated by this process.
	 */
	public void terminate() {
		trace.info("terminating process "+id);
		setRunning(false);

		//Free instruction tracks
		for (int t : instructionTracks) {
			CPU.getInstance().getMMU().getDrum().freeTrack(t);
		}

		//Free data tracks
		for (int d : dataTracks) {
			CPU.getInstance().getMMU().getDrum().freeTrack(d);
		}

		CPU.getInstance().getMMU().freePageTable(cpuState.getPtr());
	}

	public CPUState getCPUState() {
		return cpuState;
	}

	public void setCpuState(CPUState cpu) {
		this.cpuState = cpu;
	}

	public void setState(ProcessStates state,ProcessStates next) {
		if (state != null)
			this.state.setCurrent(state.getName());

		if (next != null)
			this.state.setNext(next.getName());
	}

	public String getState() {
		return state.getCurrent();
	}

	public String getNextState() {
		return state.getNext();
	}

	public boolean isProgramCardsToFollow() {
		return programCardsToFollow;
	}

	public void setProgramCardsToFollow(boolean programCardsToFollow) {
		this.programCardsToFollow = programCardsToFollow;
	}

	public void resetCurrentQuantum() {
		currentQuantum = 0;
	}

	public String toString() {
		return "PCB for process "+id+": " + 
				"\n  maxTime = "+maxTime+"; currentTime = "+currentTime+
				"\n  maxPrints = "+maxPrints+"; currentPrints = "+currPrints+
				"\n  maxQuantum = "+maxQuantum+"; currentQuantum = "+currentQuantum+
				//       "\n  pageTable = "+cpuState.getPtr()+
				"\n  outputTracks: "+outputTracks.toString()+
				"\n  instructionTracks:"+instructionTracks.toString()+
				"\n  dataTracks:"+dataTracks.toString()+
				"\n";
	}

	public int getHeaderLinedPrinted() {
		return headerLinedPrinted;
	}

	public void setHeaderLinedPrinted(int headerLinedPrinted) {
		this.headerLinedPrinted = headerLinedPrinted;
	}

	public void incrementHeaderLinedPrinted() {
		this.headerLinedPrinted++;
	}
	/**
	 * 
	 * @return
	 */
	public int getNextOutputTrack() {
		int retval = -1;
		if (outputTracks.size() > 0)
			retval = outputTracks.remove();
		return retval;
	}
	/**
	 * 
	 * @return
	 */
	public boolean outputComplete() {
		return outputTracks.isEmpty();
	}

	/**
	 * A copy of the page table for this process
	 */
	public void setPageTable(PageTable pt) {
		this.pageTable = pt;
	}

	/**
	 * return a copy of the page table for this processs
	 */
	public PageTable getPageTable() {
		//trace.info("ptr=" + cpuState.getPtr());
		if (pageTable == null)
			pageTable = getPageTable(cpuState.getPtr());
		return pageTable;
	}

	/**
	 * Get the page table stored at the given frame
	 * @param frame
	 * @return
	 */
	public PageTable getPageTable(int frame) { 
		return new PageTable(MMU.getInstance().getRam().read(cpuState.getPtr(),frame));
	}
	

	/**
	 * 
	 * @param pageNumber
	 * @return The frame number
	 */
	public int allocatePage(int pageNumber) {
		//If we haven't already allocated 4 frames in memory (page table plus 3 frames
		if (cpuState.getPtl() < pagesAllowedInMemory) { 
			System.out.println("Allocating a page");
			// Allocate a frame. Frame # returned
			int frame = MMU.getInstance().allocateFrame();
			// Update page table entry.
			//Read the current page table
			pageTable = getPageTable();
			//stick the new frame number in the correct PTE
			pageTable.getEntry(pageNumber).setBlockNum(frame);
			// store the updated page table
			pageTable.storePageTable(cpuState.getPtr());
			// update the PTL to the total number of pages in memory
			cpuState.setPtl(Math.min(CPU.getInstance().getPtl() + 1, pagesAllowedInMemory));
			trace.info("page->frame : " + pageNumber + "->" + frame);
			return frame;
		}
		// else we have used up all 4 and we need to swap out the LRU one
		else {
			System.out.println("Swapping");
			//Swap(pageNumber);
		}
		return 99;
	}
}
