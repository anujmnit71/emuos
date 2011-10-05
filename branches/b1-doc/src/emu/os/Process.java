/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.logging.Logger;

import emu.hw.CPU;
import emu.hw.HardwareInterruptException;
import emu.hw.CPU.Interrupt;

/**
 * Represents a running process. The meta data of the process is stored in the pcb. 
 * This includes the process id, the max time and maximum printed lines.
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class Process {
	/**
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * Process Meta Data
	 */
	PCB pcb;
	/**
	 * Buffers the program output
	 */
	ArrayList<String> outputBuffer;
	/**
	 * Current execution time
	 */
	int currTime;
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
	 * Create a new process instance
	 * @param id	process id
	 * @param maxTime	maximum cycles process allowed for execution
	 * @param maxPrints	maximum lines process allowed to print
	 * @param program	input buffer
	 * @param output	program output buffer
	 */
	public Process(String id, int maxTime, int maxPrints, BufferedReader program, BufferedWriter output) {
		outputBuffer = new ArrayList<String>();
		this.errorInProcess = false;
		pcb = new PCB(id, maxTime, maxPrints);
	}
	
	/**
	 * Called after program loaded successfully and the $DTA card is read.
	 * The instruction counter is reset to zero and the supervisor interrupt is 
	 * cleared. It is assumed that the process will run successfully so the termination
	 * message is set from execution without any errors.
	 */
	public void startExecution() {
		trace.fine("-->");
		trace.info("starting process "+pcb.getId());
		Kernel.getInstance().getCpu().setIc(0);
		Kernel.getInstance().getCpu().setSi(CPU.Interrupt.CLEAR);
		setTerminationStatus("Normal Execution");
		trace.fine("<--");
	}
	
	/**
	 * Writes to the programs output buffer
	 * @param data to be added to output buffer
	 */
	public void write(String data) {
		trace.fine("-->");
		trace.info("buffered output:"+data);
		outputBuffer.add(data);
		trace.fine("<--");
	}
	
	/**
	 * return output buffer to caller
	 * @return output buffer
	 */
	public ArrayList<String> getOutputBuffer() {
		return outputBuffer;
	}
	
	/**
	 * Increment time count and throw exception if max time limit is exceeded. An
	 * exception is thrown to switch to master mode to handle time interrupt.
	 * @throws HardwareInterruptException if there is an error
	 */
	public void incrementTimeCountSlave() throws HardwareInterruptException {
		if (currTime <= pcb.getMaxTime()) {
			currTime++;
		} else {
			trace.severe("max time ("+pcb.getMaxTime()+") exceeded");
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
			throw new HardwareInterruptException();
		}
	}
	
	/**
	 * Increment time count and throw exception if max time limit is exceeded. There
	 * is no need to throw an exception because the OS is in master mode. The boolean 
	 * returned will indicate a time interrupt.
	 * @return false if there is an error
	 */
	public boolean incrementTimeCountMaster()  {
		if (currTime <= pcb.getMaxTime()) {
			currTime++;
		} else {
			trace.severe("max time ("+pcb.getMaxTime()+") exceeded");
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
			return false;
		}
		return true;
	}
	
	/**
	 * get the running time of a process.
	 * @return current execution time
	 */
	public int getTime() {
		return currTime;
	}
		
	/**
	 * Increment print count and throw exception if max print limit is exceeded
	 * @return true	if current print count was incremented, false if maximum prints has been reached  
	 */
	public boolean incrementPrintCount() {
		if (currPrints <= pcb.getMaxPrints()) {
			currPrints++;
			return true;
		} 
		trace.severe("max prints ("+pcb.getMaxPrints()+") exceeded");
		Kernel.getInstance().getCpu().setIOi(Interrupt.IO);
		Kernel.getInstance().setError(2);
		return false;
	}
	
	/**
	 * get the number of printed lines of a process
	 * @return current number of lines printed.
	 */
	public int getLines() {
		return currPrints;		
	}
	
	/**
	 * Clear exiting message
	 * @param msg	to set termination status
	 */
	public void setTerminationStatus(String msg) {
		terminationStatus = msg;
		trace.finer(""+terminationStatus);
	}
	
	/**
	 * append incoming message to existing message
	 * @param msg	to append to termination status
	 */
	public void appendTerminationStatus(String msg) {
		terminationStatus += ", " + msg;
		trace.finer(""+terminationStatus);
	}
	
	/**
	 * Return the termination status of process
	 * @return	message to be printed to output.
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
	 * @return	true if there is an error in the current process, false if there is not error
	 */
	public boolean getErrorInProcess(){
		trace.finer("getErrorInProcess(): "+errorInProcess);
		return errorInProcess;
	}
	
	/**
	 * @return Id of running process
	 */
	public String getId() {
		return pcb.getId();
	}
}
