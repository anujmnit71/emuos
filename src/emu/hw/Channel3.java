package emu.hw;

import emu.hw.CPUState.Interrupt;
import emu.os.ChannelTask;


/**
 * Channel 3 Implementation.
 * @author bjdrew@gmal.com
 *
 */
public class Channel3 extends Channel {

	/**
	 * Secondary storage
	 */
	Drum drum;
	/**
	 * Main memory
	 */
	RAM memory;
	
	/**
	 * Creates Channel 3
	 * @param cycleTime The number of cycles for channel 3
	 * @param cpu A CPU reference
	 */
	public Channel3(int cycleTime, CPU cpu) {
		super(cycleTime, cpu);
		this.drum = cpu.getMMU().getDrum();
		this.memory = cpu.getMMU().getRam();
	}

	@Override
	void run() throws HardwareInterruptException {
		
		trace.info("running channel 3, task:"+task);
		
		//Switch over the possible tasks
		switch (task.getType()) {
			case GD:
				getData();
				break;
			case PD:
				putData();
				break;
			case INPUT_SPOOLING:
				inputSpool();
				break;
			case OUTPUT_SPOOLING:
				outputSpool();
				break;
			case SWAP_IN:
				swapIn();
				break;
			case SWAP_OUT:
				swapOut();
				break;
			default:
				trace.severe("Unknown task");
		}
		
		//Set CPU interrupt
		cpu.setIOi(Interrupt.IO_CHANNEL_3.getValue());
		
		busy = false;
		
		trace.info("ran channel 3, task:"+task);
		trace.finer(drum.toString());
		trace.finer(memory.toString());

	}
	
	@Override
	public void start(ChannelTask task) throws HardwareInterruptException {
		super.start(task);
		trace.info("starting channel 3");
		if (task.getBuffer() != null) {
			task.getBuffer().lock();
		}
		//cpu.clearIOi(Interrupt.IO_CHANNEL_3.getValue());
	}
	
	/**
	 * Transfer a block of data from the drum to main memory
	 * @throws HardwareInterruptException 
	 */
	private void getData() throws HardwareInterruptException {
		memory.write(0,task.getFrame(), drum.read(0,task.getTrack()));
	}
	
	/**
	 * Transfer a block of data from main memory to the drum.
	 * @throws HardwareInterruptException 
	 */
	private void putData() throws HardwareInterruptException {
		drum.write(0,task.getTrack(), memory.read(0,task.getFrame()));
	}
	
	/**
	 * Transfer a block of data from the buffer to the drum.
	 */
	private void inputSpool() {
		drum.write(0,task.getTrack(), task.getBuffer().getData());
		//Update Buffer Status
		task.getBuffer().setEmpty();
		//Unlock the buffer
		task.getBuffer().unlock();

	}

	/**
	 * Transfer a block of data from the drum to the buffer.
	 * @throws HardwareInterruptException 
	 */
	private void outputSpool() throws HardwareInterruptException {
		task.getBuffer().setData(drum.read(0,task.getTrack()));
		//Update Buffer Status
		task.getBuffer().setOutputFull();
		//Unlock the buffer
		task.getBuffer().unlock();

	}
	
	/**
	 * Reads a block of data from drum into memory
	 * @throws HardwareInterruptException
	 */
	private void swapIn() throws HardwareInterruptException {
		memory.write(0,task.getFrame(), drum.read(0,task.getTrack()));
	}
	
	/**
	 * Writes the victim frame to the drum.
	 * @throws HardwareInterruptException
	 */
	private void swapOut() throws HardwareInterruptException {
		drum.write(0,task.getTrack(), memory.read(0,task.getFrame()));
	}

	@Override
	public void close() {
		return;
	}
}
