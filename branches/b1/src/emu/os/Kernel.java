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
import emu.hw.MMU;

/**
 * Kernel for EmuOS
 * @author b.j.drew@gmail.com
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
	MMU mmu;
	/**
	 * The current process (or job)
	 */
	Process p;
	/**
	 * The buffered read for reading the input data
	 */
	BufferedReader br;
	/**
	 * The writer for writing the output file.
	 */
	BufferedWriter wr;
	
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
		mmu = new MMU();

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
		trace.info("masterMode()-->");
		trace.info("masterMode(): si="+cpu.getSi());
		switch (cpu.getSi()) {
		case READ:
			mmu.writeBlock(cpu.getIrValue(), br.readLine());
			cpu.setSi(CPU.Interupt.CONTINUE);
			break;
		case WRITE:
			p.write(mmu.readBlock(cpu.getIrValue()));
			cpu.setSi(CPU.Interupt.CONTINUE);
			break;
		case TERMINATE:
			terminate();
			break;
		case CONTINUE:
			//continue process execution
			trace.info("masterMode(): continuing process "+p.id);
			p.execute();
		}
		trace.info("masterMode()<--");
	}
	
	/**
	 * Loads the program into memory and starts execution.
	 * @throws IOException 
	 */
	public void load() throws IOException {
		
		trace.info("load()-->");
		
		String nextLine = br.readLine();

		while (nextLine != null) {
			//Check for EOJ
			if (nextLine.startsWith(Process.JOB_END)
					|| nextLine.startsWith(Process.JOB_END_ALT)) {
				
				writeBuffer();
				
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
						break;
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
		exit();

		trace.info("load()<--");
	}
	
	/**
	 * Called on program termination.
	 * @throws IOException
	 */
	public void terminate() throws IOException {
		trace.info("terminate()-->");
		wr.write("\n\n");
		load();
		trace.info("terminate()<--");
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
	public MMU getMmu() {
		return mmu;
	}

	public void slaveMode() {
		cpu.fetch(mmu);
		cpu.increment();
		cpu.execute(mmu);		
	}
	
	/**
	 * Writes the process state and buffer to the output file
	 * @throws IOException
	 */
	public void writeBuffer() throws IOException {
		trace.info("writeBuffer()-->");
		wr.write(p.id+"    Normal Execution\n");
		wr.write(cpu.toString());
		//TODO write process time and lines printed: maybe something like: wr.write(p.getTime()+" "+p.getLines());
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
