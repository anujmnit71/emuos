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
 * Representation of Secondary Memory
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public class Drum implements MemoryUnit {
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
	String [] memory; 
	
	/**
	 * static variables containing size of memory
	 */
	private final static int trackSize = 40;
	private final static int numTracks = 100;
	
	/**
	 * Set containing all tracks which are currently free
	 */
	protected List<Integer> freeTracks; 
	
	/**
	 * Constructor 
	 */
	public Drum() {
		trace.info("Initializing drum: tracks="+numTracks+"; track size="+trackSize);
		freeTracks = new ArrayList<Integer>(numTracks);
		for (int i=0; i<numTracks; i++)
			freeTracks.add(i);
		trace.finest("Free tracks: " + freeTracks.toString());
		clear();
	}
	
	public List<Integer> getFreeTracks()
	{
		return freeTracks;
	}
	
	/** 
	 * Free 1 track of secondary memory and mark it as "not allocated"
	 * @param addr
	 * @throws HardwareInterruptException
	 */
	public void freeTrack(int track) {
		trace.finer("-->");
		trace.fine("Freeing track:"+track+"->free");
		markFree(track);
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given track as allocated so it cannot be allocated to another process
	 * @param frame
	 */
	public void markAllocated(Integer track) {
		trace.finer("-->");
		trace.fine("Allocating track:"+track);
		if (!isAllocated(track))
			freeTracks.remove(track);
		String allocatedTracks = new String();
		trace.finest("Allocated tracks:");
		for (int i=0; i<numTracks; i++) {
			if (isAllocated(i))
				allocatedTracks = new String(allocatedTracks)+i+' ';
		}
		trace.fine("Allocated tracks: [ "+allocatedTracks+']');
		trace.finer("<--");
	}
	
	/** 
	 * Mark the given frame as freed so it is added back to the pool of frames available for use
	 * @param frame
	 */
	public void markFree(int frame) {
		trace.finer("-->");
		trace.info("Freed frame "+frame);
		freeTracks.add(frame);
		trace.finer("<--");
	}
	
	/** 
	 * Indicate whether the given frame has been allocated to a currently running process or not
	 * @param frame
	 * @return
	 */
	public boolean isAllocated(int frame) {
		return !freeTracks.contains(frame);
	}
	
	/**
	 * Read 1 block from memory
	 * @param frame
	 * @return
	 * @throws HardwareInterruptException 
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
	 * Load a track from memory
	 * @param addr
	 * @return
	 * @throws HardwareInterruptException 
	 */
	public String load(int track) {
		return read(track);
	}
	
	/**
	 * Write 1 track into secondary memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void write(int track, String data) {
		trace.finer("-->");
		trace.fine("Track#: "+track+" Data:"+data);
		
		//Ensure the string is 40 chars in length
		data = Utilities.padStringToLength(data, " ", trackSize, false);
		
		memory[track] = data;
		trace.finer("<--");
	}
	
	/**
	 * Store a track into memory
	 * @param addr
	 * @throws HardwareInterruptException 
	 */
	public void store(int track, String data) {
		write(track,data);
	}
	
	/**
	 * Dumps the memory contents to a single string
	 */
	public String toString() {
		int i,j;
		String dump = "\n0   |1   |2   |3   |4   |5   |6   |7   |8   |9   |\n";
		for ( i = 0; i < trackSize; i=i+1) {
			for ( j=0; j<10; j+=4) {
				dump += new String(memory[i]).substring(j,j+4)+" ";
				dump += new String(" : "+i+"\n");
			}
		}
		return dump;
	}
	
	/**
	 * Clears memory.
	 */
	public void clear() {
		memory = new String[numTracks];
		for (int i = 0; i < numTracks; i++) {
				memory[i] = BLANKS;
		}
	}
}
