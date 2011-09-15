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
import emu.os.SoftwareInterruptException.SoftwareInterruptReason;

/**
 * 
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
	public static final String JOB_END_ALT = "$END";
	
	/**
	 * Buffers the program output
	 */
	ArrayList<String> outputBuffer;
	/**
	 * The kernel
	 */
	Kernel kernel;
	/**
	 * The address where data can be loaded.
	 */
	int baseDataAddr;
	/**
	 * Process ID 
	 */
	String id;
	/**
	 * Max number of time units of execution
	 */
	int maxTime;
	/**
	 * Current execution time
	 */
	int currTime;
	/**
	 * Max number of prints
	 */
	int maxPrints;
	/**
	 * Current number of prints
	 */
	int currPrints;
	/**
	 * Message describing how the process terminated
	 */
	String terminationStatus;
	
	//TODO ProcessControlBlock
	
	/**
	 * 
	 * @param kernel Reference to the kernel instance 
	 * @param maxPrints2 
	 * @param jobData The job id
	 * @param program The input stream from which we obtain the program lines
	 * @param output The output stream we write to.
	 */
	public Process(Kernel kernel, int baseDataAddr, String id, int maxTime, int maxPrints, BufferedReader program, BufferedWriter output) {
		trace.info("id="+id+", maxTime="+maxTime+", maxPrints="+maxPrints+", baseDataAddr="+baseDataAddr);
		outputBuffer = new ArrayList<String>();
		this.kernel = kernel;
		this.baseDataAddr = baseDataAddr;
		this.id = id;
		this.maxTime = maxTime;
		this.maxPrints = maxPrints;
		currPrints = 0;
		currTime = 0;
	}
	
	/**
	 * Called after program load
	 * @throws IOException 
	 */
	public void startExecution() throws IOException {
		trace.info("startExecution()-->");
		kernel.getCpu().setIc(0);
		kernel.getCpu().setSi(CPU.Interupt.TERMINATE);
		setTerminationStatus("Normal Execution");
		trace.info("startExecution()<--");
	}
	
	/**
	 * Writes to the programs output buffer
	 * @param data
	 */
	public void write(String data) {
		outputBuffer.add(data);
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
	 * @throws SoftwareInterruptException
	 */
	public void incrementTimeCount() throws SoftwareInterruptException {
		if (currTime <= maxTime) {
			currTime++;
		} else {
			throw new SoftwareInterruptException(SoftwareInterruptReason.MAXTIME);
		}
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
	public void incrementPrintCount() throws SoftwareInterruptException {
		if (currPrints <= maxPrints) {
			currPrints++;
		} else {
			throw new SoftwareInterruptException(SoftwareInterruptReason.MAXLINES);
		}
	}
	
	/**
	 * get the number of printed lines of a process
	 * @return
	 */
	public int getLines() {
		return currPrints;		
	}
	/**
	 * 
	 * @param msg
	 */
	public void setTerminationStatus(String msg) {
		terminationStatus = msg;
	}
	/**
	 * 
	 * @return
	 */
	public String getTerminationStatus() {
		return terminationStatus;
	}
}
