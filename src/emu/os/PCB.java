package emu.os;

import java.util.logging.Logger;

/**
 * Process Control Block
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
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

	public PCB(String id, int maxTime, int maxPrints) {
		trace.info("id="+id+", maxTime="+maxTime+", maxPrints="+maxPrints);
		this.id = id;
		this.maxTime = maxTime;
		this.maxPrints = maxPrints;
	}
	
	/**
	 * @return id of process
	 */
	public String getId() {
		return id;
	}

	/**
	 * Set the process id
	 * @param id of process
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the maximum run time of process
	 */
	public int getMaxTime() {
		return maxTime;
	}

	/**
	 * Set the maximum run time of process
	 * @param maxTime of process
	 */
	public void setMaxTime(int maxTime) {
		this.maxTime = maxTime;
	}

	/**
	 * @return the maximum number of printed lines of process
	 */
	public int getMaxPrints() {
		return maxPrints;
	}

	/**
	 * Set the maximum number of printed lines of process
	 * @param maxPrints of process
	 */
	public void setMaxPrints(int maxPrints) {
		this.maxPrints = maxPrints;
	}
}
