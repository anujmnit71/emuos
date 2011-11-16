package emu.os;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import emu.hw.CPU;
import emu.hw.HardwareInterruptException;
import emu.hw.CPU.Interrupt;
import emu.os.Kernel.ErrorMessages;

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
	 * Current number of time cycles this process has been running
	 */
	int currentTime;
	/**
	 * Buffers the program output
	 */
	ArrayList<String> outputBuffer;
	/**
	 * Current execution time
	 */
	int currTime = 1;
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
	CPU cpu;
	
	private List<Integer> outputTracks;
	private List<Integer> instructionTracks;
	private List<Integer> dataTracks;

	/**
	 * was there an error in this process
	 */
	
	public PCB(String id, int maxTime, int maxPrints) {
		trace.info("id="+id+", maxTime="+maxTime+", maxPrints="+maxPrints);
		this.id = id;
		this.maxTime = maxTime;
		this.maxPrints = maxPrints;
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
	public int incrementCurrentTime() {
		currentTime = currentTime + 1;
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
		Kernel.getInstance().getCpu().setSi(CPU.Interrupt.CLEAR);
		setTerminationStatus("Normal Execution");
		trace.fine("<--");
	}
	
	/**
	 * Writes to the programs output buffer
	 * @param data
	 */
	public void write(String data) {
		trace.fine("-->");
		trace.info("buffered output:"+data);
		outputBuffer.add(data);
		trace.fine("<--");
	}
	
	/**
	 * return output buffer to caller
	 * @return
	 */
	public ArrayList<String> getOutputBuffer() {
		return outputBuffer;
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
		currTime++;
		
		if (currTime <= maxTime) {
			trace.fine("curr time: "+currTime+", max time="+maxTime);
		} else {
			trace.severe("max time ("+maxTime+") exceeded");
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
			return false;
		}
		return true;
	}
	
	/**
	 * get the running time of a process.
	 * @return
	 */
	public int getTime() {
		return currTime;
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

	public CPU getCpu() {
		return cpu;
	}

	public void setCpu(CPU cpu) {
		this.cpu = cpu;
	}
}
