package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author b.j.drew@gmail.com
 *
 */
public class Process {
	/**
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emu.os");
	
	public static final String JOB_START = "$AMJ";
	public static final String DATA_START = "$DTA";
	public static final String JOB_END = "$EOJ";
	
	/**
	 * Buffers the program output
	 */
	ArrayList<String> outputBuffer;
	/**
	 * The kernel
	 */
	Kernel kernel;
	//TODO ProcessControlBlock
	
	/**
	 * 
	 * @param kernel Reference to the kernel instance 
	 * @param jobData The job id
	 * @param program The input stream from which we obtain the program lines
	 * @param output The output stream we write to.
	 */
	public Process(Kernel kernel, String jobData, BufferedReader program, BufferedWriter output) {
		trace.info("new Process for "+jobData);
		outputBuffer = new ArrayList<String>();
		try {
			this.kernel = kernel;
			String jobLine = program.readLine();
			
			jobLine.toCharArray();
			//parse job line
			
			//loop until $DTA, load instructions into MMU
		} catch (IOException e) {
			trace.log(Level.SEVERE, "", e);
		}
	}
	
	/**
	 * Called after program load
	 * @throws IOException 
	 */
	public void startExecution() throws IOException {
		kernel.getCpu().setIc(0);
		execute();
	}
	
	/**
	 * Main execution loop
	 * @throws IOException 
	 */
	public void execute() throws IOException {
		//testing how we could return to master mode during execution
		kernel.masterMode();
	}
	
	/**
	 * Writes to the programs output buffer
	 * @param data
	 */
	public void write(String data) {
		outputBuffer.add(data);
	}
	
	/**
	 * Reads from program memory
	 * @param addr
	 * @return
	 */
	public String read(int addr) {
		//TODO Implement
		return null;
	}
}
