package emu.os;

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

	public void setMaxTime(int maxTime) {
		this.maxTime = maxTime;
	}

	public int getMaxPrints() {
		return maxPrints;
	}

	public void setMaxPrints(int maxPrints) {
		this.maxPrints = maxPrints;
	}
}
