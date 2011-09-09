package emu.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import emu.hw.CPU;
import emu.hw.CPU.Interupt;
import emu.hw.MMU;

/**
 * Kernel for EmuOS
 * @author b.j.drew@gmail.com
 *
 */
public class Kernel {
	CPU cpu;
	MMU mmu;
	Interupt i;
	Process p;
	BufferedReader br;
	BufferedWriter wr;
	
	/**
	 * 
	 * @param args
	 */
	public static final void main(String[] args) {
		
		String inputFile = args[0];
		String outputFile = args[1];
		
		//TODO IO Create streams
		
		Kernel emu;
		try {
			emu = new Kernel(inputFile, outputFile);
			emu.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	/**
	 * 
	 * @param inputFile
	 * @param outputFile
	 * @throws IOException
	 */
	public Kernel(String inputFile, String outputFile) throws IOException {
		br = new BufferedReader(new FileReader(inputFile));
		
		wr = new BufferedWriter(new FileWriter(outputFile));

		cpu = new CPU();
		cpu.setSi(CPU.Interupt.TERMINATE);
		
		mmu = new MMU();
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	public void start() throws IOException {
		switch (cpu.getSi()) {
		case READ:
			mmu.write(cpu.getIrValue(), br.readLine());
		case WRITE:
			p.write(mmu.read(cpu.getIrValue()));
		case TERMINATE:
			terminate();
		default:
			
		}
	}
	
	/**
	 * 
	 */
	public void load() {

		//TODO check for eof
		
		p = new Process(br, wr);
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	public void terminate() throws IOException {
		wr.write("\n\n");
		load();
	}
	

	
	
}
