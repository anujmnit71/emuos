/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import emu.hw.CPU;
import emu.hw.CPU.Interrupt;
import emu.hw.HardwareInterruptException;

/**
 * Represents a running process.
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
	
	public static final String JOB_START = "$AMJ";
	public static final String DATA_START = "$DTA";
	public static final String JOB_END = "$EOJ";
	
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
	 * @param id
	 * @param maxTime
	 * @param maxPrints
	 * @param program
	 * @param output
	 */
	public Process(String id, int maxTime, int maxPrints, BufferedReader program, BufferedWriter output) {
		outputBuffer = new ArrayList<String>();
		this.errorInProcess = false;
		pcb = new PCB(id, maxTime, maxPrints);
	}
	
	/**
	 * Called after program load
	 * @throws IOException 
	 */
	public void startExecution() throws IOException {
		trace.fine("-->");
		trace.info("starting process "+pcb.getId());
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
		trace.info(""+data);
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
		if (currTime <= pcb.getMaxTime()) {
			currTime++;
		} else {
			Kernel.getInstance().getCpu().setTi(Interrupt.TIME_ERROR);
			throw new HardwareInterruptException();
		}
	}
	
	/**
	 * Increment time count and throw exception if max time limit is exceeded
	 * return false if there is an error
	 */
	public boolean incrementTimeCountMaster()  {
		if (currTime <= pcb.getMaxTime()) {
			currTime++;
		} else {
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
		if (currPrints <= pcb.getMaxPrints()) {
			currPrints++;
			return true;
		} 
		
		Kernel.getInstance().getCpu().setIOi(Interrupt.IO);
		Kernel.getInstance().setError(2);
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
	
	/**
	 * 
	 * @return
	 */
	public String getId() {
		return pcb.getId();
	}
}
