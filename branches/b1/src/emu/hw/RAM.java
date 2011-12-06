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
	private final int numFrames = 30;
	private final int wordLength = 4;
	private final int wordsInFrame = 10;
	private final int wordsInMemory = wordsInFrame * numFrames;
	private final int frameSize = wordLength * wordsInFrame;
	
	/**
	 * Set containing all frames which are currently free
	 */
	protected List<Integer> freeFrames; 
	
	/**
	 * Constructor 
	 */
	public RAM() {
		trace.info("Initializing RAM: wordLength="+wordLength+",words/frame="+wordsInFrame+",frames="+numFrames);
		freeFrames = new ArrayList<Integer>(numFrames);
		for (int i=0; i<numFrames; i++)
			freeFrames.add(i);
		trace.finest("Free frames: " + freeFrames.toString());
		clear();
	}
	
	public List<Integer> getFreeFrames()
	{
		return freeFrames;
	}
	
	/** 
	 * Free 1 frame/page in memory and mark it as "not allocated"
	 * @param addr
	 * @throws HardwareInterruptException
	 */
	public void freeBlock(int addr) {
//		trace.finer("-->");
		int blockAddr = getBlockAddr(addr);
		trace.fine("Freeing frame:"+addr+"->free");
		markFree(blockAddr);
//		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as allocated so it cannot be allocated to another process
	 * @param frame
	 */
	public void markAllocated(Integer frame) {
//		trace.finer("-->");
		trace.fine("Allocating frame:"+frame);
		if (!isAllocated(frame))
			freeFrames.remove(frame);
		String allocatedFrames = new String();
		trace.finest("Allocated frames:");
		for (int i=0; i<numFrames; i++) {
			if (isAllocated(i))
				allocatedFrames = new String(allocatedFrames)+i+' ';
		}
		trace.fine("Allocated frames: [ "+allocatedFrames+']');
//		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as freed so it is added back to the pool of frames available for use
	 * @param frame
	 */
	public void markFree(int frame) {
//		trace.finer("-->");
		trace.fine("Freed frame "+(frame));
		freeFrames.add(frame);
		trace.fine(freeFrames.toString());
//		trace.finer("<--");
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
	
	public int getWordsInFrame() {
		return wordsInFrame;
	}
	/**
	 * Read 1 block from memory
	 * @param frame
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String read(int ptr, int frame) {
		
		String block = "";
		int blockAddr = frame * 10;
		
		for (int i = 0 ; i < 10 ; i++) {
			block += new String(memory[blockAddr+i]);
		}
		trace.fine("Reading frame# " + frame+"; data: "+block);
		return block;
	}
	
	/**
	 * Write 1 block into memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void write(int ptr, int frame, String data) {
//		trace.finer("-->");
		trace.fine("Frame#: "+frame+" Data:"+data);
		
		//Ensure the string in 40 chars in length
		data = Utilities.padStringToLength(data, " ", frameSize, false);
		
		int blockAddr = frame*10;
//		int blockAddr = frame;

		trace.info("  Writing frame#"+frame+"; data: "+data);
		for (int i = 0 ; i < 10 ; i++) {
			String word = data.substring(0,wordLength);
			store(ptr,blockAddr+i, word);
			data = data.substring(wordLength);
		}

		//trace.finer("Reading frame " + frame + ": "+readFrame(frame));
//		trace.finer("<--");
	}
	
	/**
	 * Load a word from memory
	 * @param addr
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String load(int ptr,int addr) {
//		trace.info("read data at addr"+addr);
		return new String(memory[addr]);
	}
	
	/**
	 * Store a word into memory
	 * @param addr
	 */
	public void store(int ptr,int addr, String data) {
//		trace.info("store <"+data+"> at "+addr);
		memory[addr] = data.toCharArray();
	}
	
	/**
	 * Dumps the memory contents to a single string
	 */
	public String toString() {
		int i;
		String dump = "RAM";
		dump += "\n0   |1   |2   |3   |4   |5   |6   |7   |8   |9   |\n";
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
		memory = new char[wordsInMemory][wordLength];
		for (int i = 0; i < memory.length; i++) {
				memory[i] = BLANKS.toCharArray();
		}
	}
}
