/**
 * Group 5
 * EmuOS: An Emulated Operating System
 * 
 * MSCS 515
 */
package emu.hw;

import java.util.ArrayList;
import java.util.List;
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
	 * blanks to initialize memory to
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
	protected int numPages;
	
	/**
	 * Set containing all frames which are currently free
	 */
	protected List<Integer> freeFrames; 
	
	/**
	 * Constructor 
	 */
	public RAM(int size, int wordLength, int wordsInBlock) {
		this.numPages = size/wordsInBlock;
		trace.info("size="+size+", wordLength="+wordLength+", wordsInBlock="+wordsInBlock+", numPages="+this.numPages);
		this.size = size;
		this.wordLength = wordLength;
		this.wordsInBlock = wordsInBlock;
		blockSize = wordLength*wordsInBlock;
		freeFrames = new ArrayList<Integer>(this.numPages);
		for (int i=0; i<30; i++)
			freeFrames.add(i);
		trace.finest("Free frames: " + freeFrames.toString());
		clear();
	}
	
	public List<Integer> getFreeFrames()
	{
		return freeFrames;
	}
	
	/**
	 * Write 1 block into memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void writeFrame(int frame, String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.info("Writing data. Frame#: "+frame+" Data:"+data);
		
		//Ensure the string in 40 chars in length
		data = Utilities.padStringToLength(data, " ", blockSize, false);
		
//		int blockAddr = getBlockAddr(addr);
		int blockAddr = frame*10;
		
		for (int i = 0 ; i < 10 ; i++) {
			String word = data.substring(0,wordLength);
			store(blockAddr+i, word);
			data = data.substring(wordLength);
		}
		trace.finer("<--");
//		trace.info(this.toString());
		trace.info("Reading frame " + frame + ": "+readFrame(frame));
	}
	
	/** 
	 * Free 1 frame/page in memory and mark it as "not allocated"
	 * @param addr
	 * @throws HardwareInterruptException
	 */
	public void freeBlock(int addr) throws HardwareInterruptException {
		trace.finer("-->");
		int blockAddr = getBlockAddr(addr);
		trace.info("Freeing frame:"+addr+"->free");
		markFree(blockAddr);
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as allocated so it cannot be allocated to another process
	 * @param frame
	 */
	public void markAllocated(Integer frame) {
		trace.finer("-->");
		trace.info("Allocating frame:"+frame);
		if (!isAllocated(frame))
			freeFrames.remove(frame);
		String allocatedFrames = new String();
		trace.finest("Allocated frames:");
		for (int i=0; i<numPages; i++) {
			if (isAllocated(i))
				allocatedFrames = new String(allocatedFrames)+i+' ';
		}
		trace.info("Allocated frames: [ "+allocatedFrames+']');
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as freed so it is added back to the pool of frames available for use
	 * @param frame
	 */
	public void markFree(int frame) {
		trace.finer("-->");
		freeFrames.add(frame);
		trace.finer("<--");
	}
	
	/** 
	 * Indicate whether the given frame has been allocated to a currently running process or not
	 * @param frame
	 * @return
	 */
	public boolean isAllocated(int frame) {
		return !freeFrames.contains(frame);
	}
	
	/**
	 * Read 1 block from memory
	 * @param frame
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String readFrame(int frame) throws HardwareInterruptException {
		
		String block = "";
//		int blockAddr = getBlockAddr(addr);
		int blockAddr = frame * 10;
		
		for (int i = 0 ; i < 10 ; i++) {
			block += new String(memory[blockAddr+i]);
		}
		trace.info("Reading frame# " + frame+"; data: "+block);
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

		trace.fine("getBlockAddr: offset: " +blockOffset+", frame#: "+blockAddr);
		
		return blockOffset; /* AMC - this used to be blockAddr - that seemed wrong */
	}
	
	public int getWordsInBlock() {
		return wordsInBlock;
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
		String dump = "\n0   |1   |2   |3   |4   |5   |6   |7   |8   |9   |\n";
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
