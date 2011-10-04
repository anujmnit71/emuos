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
	 * Constructor of RAM
	 * @param size	of memory
	 * @param wordLength	length of word
	 * @param wordsInBlock	number of words in a block of memory
	 */
	public RAM(int size, int wordLength, int wordsInBlock) {
		this.numPages = size/wordsInBlock;
		trace.info("wordLength="+wordLength+",words/frame="+wordsInBlock+",frames="+numPages);
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
	
    /**
     * @return List of free frames
     */
	protected List<Integer> getFreeFrames()
	{
		return freeFrames;
	}
	
	/**
	 * Write 1 block into memory
	 * \dot digraph ram_write {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * write_0 [label="pad data to length"];
	 * write_2 [label="block address = frame * 10"];
	 * write_4 [label="i = 0"];
	 * write_6 [label="word = data[0]...data[word length] "];
	 * write_8 [label="store (block address + i , word )"];
	 * write_10 [label="data = data[word length]...data[data length] "];
	 * write_12 [label="i++"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * write_1 [label="i < 10"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * write_begin [label="begin (frame, data)", color="chartreuse"];
	 * write_return [label="return block", color="firebrick1"];
	 * 
	 * {rank = same; write_return write_12};
	 * write_begin -> write_0 -> write_2 -> write_4 -> write_1 -> write_6 [label="true"];
	 * write_6 -> write_8 -> write_10 -> write_12 -> write_1;
	 * write_1 -> write_return [label="false"];
	 * } \enddot
	 * @param frame	number
	 * @param data	to be written to frame
	 */
	public void write(int frame, String data) {
		trace.finer("-->");
		trace.fine("Frame#: "+frame+" Data:"+data);
		
		//Ensure the string in 40 chars in length
		data = Utilities.padStringToLength(data, " ", blockSize, false);
		
		int blockAddr = frame*10;
		
		for (int i = 0 ; i < 10 ; i++) {
			String word = data.substring(0,wordLength);
			store(blockAddr+i, word);
			data = data.substring(wordLength);
		}

		//trace.finer("Reading frame " + frame + ": "+readFrame(frame));
		trace.finer("<--");
	}
	
	/** 
	 * Free 1 frame/page in memory and mark it as "not allocated"
	 * \dot digraph ram_freeBlock {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * freeBlock_0[label="block address = getBlockAddr ( address )"];
	 * freeBlock_2 [label="markFree ( block address )"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * freeBlock_begin [label="begin ( address )", color="chartreuse"];
	 * freeBlock_return [label="return", color="firebrick1"];
	 * 
	 * freeBlock_begin -> freeBlock_0 -> freeBlock_2 -> freeBlock_return;
	 * } \enddot
	 * @param addr of block to be freed
	 */
	protected void freeBlock(int addr) {
		trace.finer("-->");
		int blockAddr = getBlockAddr(addr);
		trace.fine("Freeing frame:"+addr+"->free");
		markFree(blockAddr);
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as allocated so it cannot be allocated to another process
	 * \dot digraph ram_markAllocated {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * markAllocated_0 [label="remove from free frames list"];
	 * markAllocated_2 [label="i = 0"];
	 * markAllocated_4 [label="allocated frame = i <space>"];
	 * markAllocated_6 [label="i++"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * markAllocated_1 [label="if frame is not allocated"];
	 * markAllocated_3 [label="i < numPages"];
	 * markAllocated_5 [label="if frame i is allocated"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * markAllocated_begin [label="begin ( frame )", color="chartreuse"];
	 * markAllocated_return [label="return", color="firebrick1"];
	 * 
	 * {rank = same; markAllocated_return markAllocated_6};
	 * markAllocated_begin -> markAllocated_1;
	 * markAllocated_1 -> markAllocated_0 [label="true"];
	 * markAllocated_1 -> markAllocated_2 [label="false"];
	 * markAllocated_0 -> markAllocated_2
	 * markAllocated_2 -> markAllocated_3
	 * markAllocated_3 -> markAllocated_5 [label="true"];
	 * markAllocated_5 -> markAllocated_4 [label="true"];
	 * markAllocated_4 -> markAllocated_6 -> markAllocated_3;
	 * markAllocated_5 -> markAllocated_6 [label="false"];
	 * markAllocated_3 -> markAllocated_return [label="false"];
	 * } \enddot
	 * @param frame	to be allocated
	 */
	protected void markAllocated(Integer frame) {
		trace.finer("-->");
		trace.fine("Allocating frame:"+frame);
		if (!isAllocated(frame))
			freeFrames.remove(frame);
		String allocatedFrames = new String();
		trace.finest("Allocated frames:");
		for (int i=0; i<numPages; i++) {
			if (isAllocated(i))
				allocatedFrames = new String(allocatedFrames)+i+' ';
		}
		trace.fine("Allocated frames: [ "+allocatedFrames+']');
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as freed so it is added back to the pool of frames available for use
	 * @param frame	to be freed
	 */
	protected void markFree(int frame) {
		trace.finer("-->");
		freeFrames.add(frame);
		trace.finer("<--");
	}
	
	/** 
	 * Indicate whether the given frame has been allocated to a currently running process or not
	 * @param frame	in question
	 * @return true if frame is allocated otherwise false
	 */
	protected boolean isAllocated(int frame) {
		return !freeFrames.contains(frame);
	}
	
	/**
	 * Read 1 block from memory
	 * \dot digraph ram_read {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * read_0[label="block address = frame * 10"];
	 * read_2 [label="i = 0"];
	 * read_4 [label="block += memory [block address + i]"];
	 * read_6 [label="i++"];
	 * 
	 * node [shape="diamond",color="blue",fontsize="8",fontname="Helvetica"];
	 * read_1 [label="i < 10"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * read_begin [label="begin (frame)", color="chartreuse"];
	 * read_return [label="return block", color="firebrick1"];
	 * 
	 * {rank = same; read_return read_6};
	 * read_begin -> read_0 -> read_2 -> read_1;
	 * read_1 -> read_4 [label="true"];
	 * read_4 -> read_6 -> read_1;
	 * read_1 -> read_return [label="false"];
	 * } \enddot
	 * @param frame	to be read
	 * @return contents of block
	 */
	public String read(int frame) {
		
		String block = "";
		int blockAddr = frame * 10;
		
		for (int i = 0 ; i < 10 ; i++) {
			block += new String(memory[blockAddr+i]);
		}
		trace.fine("Reading frame# " + frame+"; data: "+block);
		return block;
	}
	
	/**
	 * Converts any address into a block address
	 * \dot digraph ram_getBlockAddr {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * getBlockAddr_0[label="offset = address % 10"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * getBlockAddr_begin [label="begin (address)", color="chartreuse"];
	 * getBlockAddr_return [label="return block offset", color="firebrick1"];
	 * 
	 * getBlockAddr_begin -> getBlockAddr_0 -> getBlockAddr_return;
	 * } \enddot
	 * @param addr	to be converted
	 * @return	frame of address
	 */
	private int getBlockAddr(int addr) {
		int blockOffset = addr % 10;
		int blockAddr = addr - blockOffset;

		trace.fine("getBlockAddr: offset: " +blockOffset+", frame#: "+blockAddr);
		
		return blockOffset; /* AMC - this used to be blockAddr - that seemed wrong */
	}
	
	/**
	 * @return	the number of words in a block
	 */
	protected int getWordsInBlock() {
		return wordsInBlock;
	}
	
	/**
	 * Load a word from memory
	 * @param addr	in memory
	 * @return	word at address in memory
	 */
	public String load(int addr) {
		return new String(memory[addr]);
	}
	
	/**
	 * Store a word into memory
	 * @param addr	in memory
	 * @param data	to store into addr
	 */
	public void store(int addr, String data) {
		//trace.info("store <"+data+"> at "+addr);
		memory[addr] = data.toCharArray();
	}
	
	/**
	 * Dump the memory contents to a single string
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
	 * Clear the entire contents of memory.
	 */
	public void clear() {
		memory = new char[size][wordLength];
		for (int i = 0; i < memory.length; i++) {
				memory[i] = BLANKS.toCharArray();
		}
	}
}
