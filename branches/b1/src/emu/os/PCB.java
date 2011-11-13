package emu.os;

import java.util.List;
import java.util.logging.Logger;

/**
 * Process Control Block
 * @author b.j.drew@gmail.com
 *
 */
public class PCB {
	/**
	 * Tracer
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * Process ID 
	 */
	String id;
	/**
	 * Max number of time units of execution
	 */
	int maxTime;
	/**
	 * Max number of prints
	 */
	int maxPrints;
	/*
	 * Current number of time cycles this process has been running
	 */
	int currentTime;
	
	private List<Integer> outputTracks;
	private List<Integer> instructionTracks;
	private List<Integer> dataTracks;

	/**
	 * was there an error in this process
	 */
	
	public PCB(String id, int maxTime, int maxPrints) {
		trace.info("id="+id+", maxTime="+maxTime+", maxPrints="+maxPrints);
		this.id = id;
		this.maxTime = maxTime;
		this.maxPrints = maxPrints;
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getMaxTime() {
		return maxTime;
	}

	public int setMaxTime(int maxTime) {
		this.maxTime = maxTime;
		return maxTime;
	}

	public int getMaxPrints() {
		return maxPrints;
	}

	public int setMaxPrints(int maxPrints) {
		this.maxPrints = maxPrints;
		return maxPrints;
	}
	
	public int getCurrentTime() {
		return currentTime;
	}
	public int incrementCurrentTime() {
		currentTime = currentTime + 1;
		return currentTime;
	}
	public int getCurrentPrints() {
		return outputTracks.size();
	}
	public int bufferOutputLine(int track) {
		outputTracks.add(track);
		return outputTracks.size();
	}
	public void addInstructionTrack(int track) {
		instructionTracks.add(track);
	}
	public void addDataTrack(int track) {
		dataTracks.add(track);
	}
	public int getNumInstructionTracks() {
		return instructionTracks.size();
	}
	public int getNumDataTracks() {
		return dataTracks.size();
	}
}
