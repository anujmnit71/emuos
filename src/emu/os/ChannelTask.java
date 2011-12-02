package emu.os;

import emu.hw.Buffer;

/**
 * All of the information a channel needs to perform a task.
 * @author bjdrew@gmail.com
 *
 */
public class ChannelTask {
	/**
	 * An indentifier of the specific task to perform.
	 * @author bjdrew@gmail.com
	 *
	 */
	public enum TaskType {
		SWAP_IN,
		SWAP_OUT,
		GD,
		PD,
		INPUT_SPOOLING, 
		OUTPUT_SPOOLING
	}
	
	/**
	 * The task to perform.
	 */
	TaskType type;
	/**
	 * The buffer to use.
	 */
	Buffer buffer;
	/**
	 * The memory frame to read/write from/to
	 */
	int frame = -1;
	/**
	 * The drum track to read/write from/to
	 */
	int track = -1;
	/**
	 * Miscellaneous value
	 * @return
	 */
	Object misc;
	
	public TaskType getType() {
		return type;
	}
	public void setType(TaskType type) {
		this.type = type;
	}
	public Buffer getBuffer() {
		return buffer;
	}
	public void setBuffer(Buffer buffer) {
		this.buffer = buffer;
	}
	public int getFrame() {
		return frame;
	}
	public void setFrame(int frame) {
		this.frame = frame;
	}
	public int getTrack() {
		return track;
	}
	public void setTrack(int track) {
		this.track = track;
	}
	public Object getMisc() {
		return misc;
	}
	public void setMisc(Object obj) {
		misc = obj;
	}
	public String toString() {
		return "[ ChannelTask :: task="+type+", buffer="+buffer+", frame="+frame+", track="+track+" ]";
	}
}
