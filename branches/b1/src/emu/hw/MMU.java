package emu.hw;

import java.util.logging.Logger;

/**
 * Memory Management Unit
 * 
 * @author b.j.drew@gmail.com
 *
 */
public class MMU {
	/**
	 * For tracing
	 */
	Logger trace = Logger.getLogger("emu.hw");
	/**
	 * Memory array
	 */
	char [][] memory; 
	
	/**
	 * Constructor 
	 */
	public MMU() {
		 memory = new char[100][4];
	}
	
	/**
	 * Write 1 block into memory
	 * @param addr
	 */
	public void writeBlock(int addr, String data) {

		trace.info("writeBlock(): "+addr+":"+data);
		
		int blockAddr = getBlockAddr(addr);
		
		for (int i = 0 ; i < 10 ; i++) {
			String word = data.substring(0,4);
			store(blockAddr+i, word);
			data = data.substring(4);
		}
		
	}
	
	/**
	 * Read 1 block from memory
	 * @param addr
	 * @return
	 */
	public String readBlock(int addr) {
		
		trace.info("readBlock(): "+addr);
		
		String block = "";
		int blockAddr = getBlockAddr(addr);
		
		for (int i = 0 ; i < 10 ; i++) {
			block += new String(memory[blockAddr+i]);
		}
		
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
	 */
	public String load(int addr) {
		return new String(memory[addr]);
	}
	
	/**
	 * Store a word into memory
	 * @param addr
	 */
	public void store(int addr, String data) {
		memory[addr] = data.toCharArray();
	}
	
	/**
	 * Dumps the memory contents to a single string
	 */
	public String toString() {
		String dump = "";
		for (char[] c : memory) {
			dump += new String(c)+"\n";
		}
		return dump;
	}
}
