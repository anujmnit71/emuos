package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	static Logger trace = Logger.getLogger("emu.os");
	/**
	 * CPU instance
	 */
	CPU cpu;
	public CPU getCpu() {
		return cpu;
	}

	public MMU getMmu() {
		return mmu;
	}

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
	 * 
	 * @param args
	 */
	public static final void main(String[] args) {
		
		String inputFile = args[0];
		String outputFile = args[1];
		
		Kernel emu;
		try {
			emu = new Kernel(inputFile, outputFile);
			emu.start();
		} catch (IOException e) {
			trace.log(Level.SEVERE, "IOException", e);
		}

	}
	
	/**
	 * 
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
		}
	}
	
	/**
	 * Called when control needs to be passed back to the OS
	 * @throws IOException
	 */
	public void masterMode() throws IOException {
		trace.info("masterMode()-->");
		switch (cpu.getSi()) {
		case READ:
			mmu.writeBlock(cpu.getIrValue(), br.readLine());
		case WRITE:
			p.write(mmu.readBlock(cpu.getIrValue()));
		case TERMINATE:
			terminate();
		}
	}
	
	/**
	 * Starts new process.
	 * @throws IOException 
	 */
	public void load() throws IOException {
		trace.info("load()-->");
		String jobData = br.readLine();
		if (jobData != null && jobData.startsWith(Process.JOB_START)) {
			trace.info("load(): Loading job "+jobData);
			p = new Process(this, jobData, br, wr);
			p.execute();
		}
		else {
			trace.info("load(): No Job to load, exiting");
		}
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	public void terminate() throws IOException {
		trace.info("terminate()-->");
		wr.write("\n\n");
		load();
	}
}
