package emu.hw;

import java.util.logging.Logger;

import emu.os.ChannelTask;

/**
 * An abstract class to represent a channel.
 * @author bjdrew@gmail.com
 *
 */
public abstract class Channel {
	/**	
	 * For tracing
	 */
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * True if the channel is currently running
	 */
	boolean busy;
	/**
	 * The number of time units the channel needs to perform its task.
	 */
	int cycleTime;
	/**
	 * The current number of time units the channel has been running (since last started)
	 */
	int currCycleTime;
	/**
	 * A CPU reference for raising interrupts.
	 */
	CPU cpu;
	/**
	 * A definition of the task to perform.
	 */
	ChannelTask task;
	
	/**
	 * Create a channel.
	 * @param cycleTime 
	 * @param cpu 
	 */
	public Channel(int cycleTime, CPU cpu) {
		this.cycleTime = cycleTime;
		this.cpu = cpu;
	}

	public boolean isBusy() {
		return busy;
	}

	public void setBusy(boolean busy) {
		this.busy = busy;
	}

	public ChannelTask getTask() {
		return task;
	}
	
	/**
	 * Runs the channel
	 * @throws HardwareInterruptException
	 */
	abstract void run () throws HardwareInterruptException;
	
	/**
	 * Starts the channel.
	 * @param task
	 */
	public void start(ChannelTask task) {
		this.task = task;
		busy = true;
		currCycleTime = 0;
	}
	
	/**
	 * Increments the channel clock.  If the time is up, run the task.
	 * @throws HardwareInterruptException 
	 */
	public void increment() throws HardwareInterruptException {
		trace.info("increment "+currCycleTime);
		currCycleTime++;
		
		if (currCycleTime == cycleTime) {
			trace.fine("running "+task.getType());
			run();
		}
	}

}
