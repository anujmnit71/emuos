package emu.os;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import emu.hw.CPU;
import emu.hw.CPUState;
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
	private List<Integer> outputTracks;
	/**
	 * Tracks containing programs instructions
	 */
	private List<Integer> instructionTracks;
	/**
	 * Tracks containing program data
	 */
	private List<Integer> dataTracks;
	/**
	 * The process state
	 */
	State state;
	/**
	 * Set true if program cards need to be spooled, 
	 * otherwise it is assumed data cards are to follow
	 */
	boolean programCardsToFollow = true;
	
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
	private enum ProcessStates {
		READY ("ready"),
		EXECUTE ("execute"),
		IO ("io"),
		MEMORY ("memory"),
		TERMINATE ("terminate"),
		SWAP ("swap"),
		SPOOL ("spool");
		
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
		this.outputTracks = new ArrayList<Integer>();
		this.instructionTracks = new ArrayList<Integer>();
		this.dataTracks = new ArrayList<Integer>();
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
	public void addDataTrack(int track) {
		dataTracks.add(track);
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
	public void incrementTimeCountSlave() throws HardwareInterruptException {
		if (!incrementTime()) {
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
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
		currentTime++;
		currentQuantum++;

		if (currentQuantum <= maxQuantum) {
			trace.fine("pid: "+id+", currentQuantum: "+currentQuantum);
		}
		else {
			trace.info("quantum reached for "+id);
			//TODO What now?
		}
		if (currentTime <= maxTime) {
			trace.fine("curr time: "+currentTime+", max time="+maxTime);
		} else {
			trace.severe("max time ("+maxTime+") exceeded");
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
			return false;
		}
		return true;
	}
		
	/**
	 * Increment print count and throw exception if max print limit is exceeded
	 * @throws SoftwareInterruptException
	 */
	public boolean incrementPrintCount() {
		
		currPrints++;
		
		if (currPrints <= maxPrints) {
			return true;
		} 
		trace.severe("max prints ("+maxPrints+") exceeded");
		//Kernel.getInstance().getCpu().setIOi(Interrupt.IO);
		//Kernel.getInstance().setError(ErrorMessages.LINE_LIMIT_EXCEEDED); //TODO fix?
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
	
	public void terminate() {
		trace.info("terminating process "+id);
		setRunning(false);
	}

	public CPUState getCpuState() {
		return cpuState;
	}

	public void setCpuState(CPUState cpu) {
		this.cpuState = cpu;
	}

	public boolean isProgramCardsToFollow() {
		return programCardsToFollow;
	}

	public void setProgramCardsToFollow(boolean programCardsToFollow) {
		this.programCardsToFollow = programCardsToFollow;
	}
	
	public String toString() {
		return "PCB for process "+id+": " + 
	           "\n  maxTime = "+maxTime+"; currentTime = "+currentTime+
	           "\n  maxPrints = "+maxPrints+"; currentPrints = "+currPrints+
	           "\n  maxQuantum = "+maxQuantum+"; currentQuantum = "+currentQuantum+
	           "\n  outputTracks: "+outputTracks.toString()+
	           "\n  instructionTracks:"+instructionTracks.toString()+
	           "\n  dataTracks:"+dataTracks.toString()+
	           "\n";
	}
}
