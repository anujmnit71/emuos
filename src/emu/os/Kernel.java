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
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import emu.hw.CPU;
import emu.hw.HardwareInterruptException;
import emu.hw.PhysicalMemory;

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
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * CPU instance
	 */
	CPU cpu;
	/**
	 * MMU instance
	 */
	PhysicalMemory mmu;
	/**
	 * The current process (or job)
	 */
	Process p;
	/**
	 * The buffered reader for reading the input data
	 */
	BufferedReader br;
	/**
	 * The writer for writing the output file.
	 */
	BufferedWriter wr;
	/**
	 * Boot sector
	 */
	String bootSector = "H                                       ";
	/**
	 * Starts EmuOS
	 * @param args
	 */
	public static final void main(String[] args) {
		
		if (args.length != 2) {
			trace.severe("I/O files missing.");
			System.exit(1);
		}
		
		String inputFile = args[0];
		String outputFile = args[1];
		
		try {
		    // Create an appending file handler
		    FileHandler handler = new FileHandler("emuos.log");
		    handler.setFormatter(new SimpleFormatter());

		    // Add to the desired logger
		    trace.addHandler(handler);
		} catch (IOException e) {
		}
		
		Kernel emu;
		try {
			emu = new Kernel(inputFile, outputFile);
			emu.start();
		} catch (IOException ioe) {
			trace.log(Level.SEVERE, "IOException", ioe);
		} catch (Exception e){
			trace.log(Level.SEVERE, "Exception", e);
		}

	}
	
	/**
	 * Constructor
	 * @param inputFile
	 * @param outputFile
	 * @throws IOException
	 */
	public Kernel(String inputFile, String outputFile) throws IOException {
		
		//Init HW
		cpu = new CPU();
		mmu = new PhysicalMemory(300,4);

		//Init I/O
		br = new BufferedReader(new FileReader(inputFile));
		wr = new BufferedWriter(new FileWriter(outputFile));

	}
	
	/**
	 * Starts the OS
	 * @throws IOException
	 */
	public void start() throws IOException {
		trace.info("start()-->");
		try {
			cpu.setSi(CPU.Interupt.TERMINATE);
			boot();
			masterMode();
		} finally {
			br.close();
			wr.close();
			//Dump memory
			trace.info("\n"+mmu.toString());
			//Dump memory
			trace.info("\n"+cpu.toString());

		}
	}
	
	/**
	 * Called when control needs to be passed back to the OS
	 * @throws IOException
	 */
	public void masterMode() throws IOException {
		boolean done = false;
		trace.info("masterMode()-->");
		trace.info("masterMode(): si="+cpu.getSi());
		while (!done) {
			try {
				slaveMode();
				p.incrementTimeCount();
			}
			catch (HardwareInterruptException hire){
				trace.log(Level.SEVERE, "HardwareInteruptException", hire);
				try {
					switch (cpu.getSi()) {
						case READ:
							mmu.writeBlock(cpu.getIrValue(), br.readLine());
							break;
						case WRITE:
						try {
							p.incrementPrintCount();
						} catch (SoftwareInterruptException e) {
							trace.log(Level.SEVERE, "SoftwareInteruptException", e);
							String msg = e.getMessage();
							trace.info(msg);
							p.setTerminationStatus(msg);
							done = terminate();
						}
							p.write(mmu.readBlock(cpu.getIrValue()));
							break;
						case TERMINATE:
							done = terminate();
							break;
					}
					
					p.incrementTimeCount();

				} catch (SoftwareInterruptException sire) {
					trace.log(Level.SEVERE, "SoftwareInteruptException", sire);
					String msg = sire.getMessage();
					trace.info(msg);
					p.setTerminationStatus(msg);
					done = terminate();
				}

			} catch (SoftwareInterruptException sire) {
				trace.log(Level.SEVERE, "SoftwareInteruptException", sire);
				String msg = sire.getMessage();
				trace.info(msg);
				p.setTerminationStatus(msg);
				done = terminate();
			}
		}

		trace.info("masterMode()<--");
	}
	/**
	 * load the halt instruction into memory 
	 */
	public void boot() {
		mmu.writeBlock(0, bootSector);
	}
	
	/**
	 * Loads the program into memory and starts execution.
	 * @throws IOException 
	 */
	public boolean load() throws IOException {
		
		trace.info("load()-->");
		
		String nextLine = br.readLine();

		while (nextLine != null) {
			//Check for EOJ
			if (nextLine.startsWith(Process.JOB_END)
					|| nextLine.startsWith(Process.JOB_END_ALT)) {
				
				writeProccess();
				
				trace.info("load(): Finished job "+p.id);
				
				//read next line
				nextLine = br.readLine();
				trace.info(nextLine);
			}
			
			if (nextLine == null || nextLine.isEmpty()) {
				trace.info("load(): skipping empty line...");
				nextLine = br.readLine();
				//exit();
				continue;
			}
			else if (nextLine.startsWith(Process.JOB_START)) {
				trace.info("load(): Loading job "+nextLine);
				
				//Clear memory
				mmu.clear();
				
				//Parse Job Data
				String id = nextLine.substring(4, 8);
				int maxTime = Integer.parseInt(nextLine.substring(8, 12));
				int maxPrints = Integer.parseInt(nextLine.substring(12, 16));
				
				//Reads first program line
				String programLine = br.readLine();
				int base = 0;
				
				//Write each block of program lines into memory
				while (programLine != null) {
					
					if (programLine.equals(Process.JOB_END) 
							|| programLine.equals(Process.JOB_END_ALT)
							|| programLine.equals(Process.JOB_START)) {
						trace.info("breaking on "+programLine);
						break;
					}
					else if (programLine.equals(Process.DATA_START)) {
						p = new Process(this, base, id, maxTime, maxPrints, br, wr);
						p.startExecution();
						return false;
					}

					mmu.writeBlock(base, programLine);
					base+=10;
					programLine = br.readLine();
				}

			}
			else {
				trace.info("Unexpected line:"+nextLine);
				trace.severe("load() Program error for "+p.id);
				nextLine = br.readLine();
			}
		}

		trace.info("load(): No more jobs, exiting");

		trace.info("load()<--");
		return true;
	}
	
	/**
	 * Called on program termination.
	 * @throws IOException
	 */
	public boolean terminate() throws IOException {
		boolean retval=false;
		trace.info("terminate()-->");
		wr.write("\n\n");
		retval = load();
		trace.info("terminate("+retval+")<--");
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
	 * Returns the MMU
	 * @return
	 */
	public PhysicalMemory getMmu() {
		return mmu;
	}

	/**
	 * Slave execution cycle 
	 * @throws HardwareInterruptException
	 * @throws SoftwareInterruptException
	 */
	public void slaveMode() throws HardwareInterruptException, SoftwareInterruptException {
		cpu.fetch(mmu);
		cpu.increment();
		cpu.execute(mmu);
	}
	
	/**
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public void writeProccess() throws IOException {
		trace.info("writeBuffer()-->");
		wr.write(p.id+"    "+p.getTerminationStatus()+"\n");
		wr.write(cpu.getState());
		wr.write("    "+p.getTime()+"    "+p.getLines());
		wr.newLine();
		wr.newLine();
		wr.newLine();
		
		ArrayList<String> buf = p.getOutputBuffer();
		for (String line : buf) {
			 wr.write(line);
			 wr.newLine();
		}
		wr.flush();
	}
}
