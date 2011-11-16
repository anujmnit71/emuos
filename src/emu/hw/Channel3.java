package emu.hw;

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
	
	private Channel3(int cycleTime, CPU cpu) {
		super(cycleTime, cpu);
	}

	/**
	 * Creates Channel 3
	 * @param cycleTime The number of cycles for channel 3
	 * @param cpu A CPU reference
	 * @param cardReader The input file.
	 */
	public Channel3(int cycleTime, CPU cpu, Drum drum, RAM memory) {
		super(cycleTime, cpu);
		this.drum = drum;
		this.memory = memory;
		
	}

	@Override
	void run() throws HardwareInterruptException {
		
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
		cpu.setIOi(CPU.Interrupt.IO_CHANNEL_3.getValue());
		
		busy = false;

	}
	
	@Override
	public void start(ChannelTask task) {
		super.start(task);
		cpu.clearIOi(cpu.getIOi().getValue() - CPU.Interrupt.IO_CHANNEL_3.getValue());
	}
	
	/**
	 * Transfer a block of data from the drum to main memory
	 * @throws HardwareInterruptException 
	 */
	private void getData() throws HardwareInterruptException {
		memory.write(task.getFrame(), drum.read(task.getTrack()));
	}
	
	/**
	 * Transfer a block of data from main memory to the drum.
	 * @throws HardwareInterruptException 
	 */
	private void putData() throws HardwareInterruptException {
		drum.write(task.getTrack(), memory.read(task.getFrame()));
	}
	
	/**
	 * Transfer a block of data from the buffer to the drum.
	 */
	private void inputSpool() {
		drum.write(task.getTrack(), task.getBuffer().getData());
		//Update Buffer Status
		task.getBuffer().setEmpty();
	}

	/**
	 * Transfer a block of data from main memory to the buffer.
	 * @throws HardwareInterruptException 
	 */
	private void outputSpool() throws HardwareInterruptException {
		task.getBuffer().setData(memory.read(task.getFrame()));
		//Update Buffer Status
		task.getBuffer().setOutputFull();
	}
	
	private void swapIn() {
		//TODO Implement
	}
	
	private void swapOut() {
		//TODO Implement
	}
}
