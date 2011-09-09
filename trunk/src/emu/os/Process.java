package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * 
 * @author b.j.drew@gmail.com
 *
 */
public class Process {
	public static final String JOB_START = "$AMJ";
	public static final String DATA_START = "$DTA";
	public static final String JOB_END = "$EOJ";
	
	ArrayList<String> outputBuffer;
	//TODO ProcessControlBlock
	
	/**
	 * 
	 * @param program
	 * @param output
	 */
	public Process(BufferedReader program, BufferedWriter output) {
		outputBuffer = new ArrayList<String>();
		try {
			String jobLine = program.readLine();
			
			jobLine.toCharArray();
			//parse job line
			
			//loop until $DTA, load instructions into MMU
			
			
			
			//
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//read program cards
	}
	
	/**
	 * Called after program load
	 */
	public void startExecution() {
		// TODO cpu.setIc(0);
		execute();
	}
	
	/**
	 * Main execution loop
	 */
	public void execute() {
		
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
		return null;
	}
}
