/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.logging.Logger;

import emu.util.Utilities;

/**
 * Representation of Physical Memory
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class RAM implements MemoryUnit {
	/**
	 * For tracing
	 */
	Logger trace = Logger.getLogger("emuos");
	/**
	 * blanks to initialize memeory to
	 */
	public static final String BLANKS = "    ";
	/**
	 * Memory array
	 */
	char [][] memory; 
	
	/**
	 * variables containing size of memory
	 */
	protected int size;
	protected int wordLength;
	protected int wordsInBlock;
	protected int blockSize;
	
	/**
	 * Constructor 
	 */
	public RAM(int size, int wordLength, int wordsInBlock) {
		trace.info("size="+size+", wordsInBlock="+wordLength+", wordsInBlock="+wordsInBlock);
		this.size = size;
		this.wordLength = wordLength;
		this.wordsInBlock = wordsInBlock;
		blockSize = wordLength*wordsInBlock;
		clear();
	}
	
	/**
	 * Write 1 block into memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void writeBlock(int addr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.info(addr+"<-"+data);
		
		//Ensure the string in 40 chars in length
		data = Utilities.padStringToLength(data, " ", blockSize, false);
		
		int blockAddr = getBlockAddr(addr);
		
		for (int i = 0 ; i < 10 ; i++) {
			String word = data.substring(0,wordLength);
			store(blockAddr+i, word);
			data = data.substring(wordLength);
		}
		trace.finer("<--");
	}
	
	/**
	 * Read 1 block from memory
	 * @param addr
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String readBlock(int addr) throws HardwareInterruptException {
		
		String block = "";
		int blockAddr = getBlockAddr(addr);
		
		for (int i = 0 ; i < 10 ; i++) {
			block += new String(memory[blockAddr+i]);
		}
		trace.info(addr+"->"+block);
		return block;
	}
	
	/**
	 * Converts any address into a block address
	 * @param addr
	 * @return
	 */
	private int getBlockAddr(int addr) {
		int blockOffset = addr % 10;
		int blockAddr = addr - blockOffset;

		trace.fine(blockOffset+","+blockAddr);
		
		return blockAddr;
	}
	
	/**
	 * Load a word from memory
	 * @param addr
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String load(int addr) throws HardwareInterruptException {
		return new String(memory[addr]);
	}
	
	/**
	 * Store a word into memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void store(int addr, String data) throws HardwareInterruptException {
		//trace.info("store <"+data+"> at "+addr);
		memory[addr] = data.toCharArray();
	}
	
	/**
	 * Dumps the memory contents to a single string
	 */
	public String toString() {
		int i;
		String dump = "0   |1   |2   |3   |4   |5   |6   |7   |8   |9   |\n";
		for ( i = 0; i < memory.length; i=i+10) {
			dump += new String(memory[i])+" ";
			dump += new String(memory[i+1])+" ";
			dump += new String(memory[i+2])+" ";
			dump += new String(memory[i+3])+" ";
			dump += new String(memory[i+4])+" ";
			dump += new String(memory[i+5])+" ";
			dump += new String(memory[i+6])+" ";
			dump += new String(memory[i+7])+" ";
			dump += new String(memory[i+8])+" ";
			dump += new String(memory[i+9])+"|";
			dump += i+"\n";
		}
		return dump;
	}
	
	/**
	 * Clears memory.
	 */
	public void clear() {
		memory = new char[size][wordLength];
		for (int i = 0; i < memory.length; i++) {
				memory[i] = BLANKS.toCharArray();
		}
	}
}
