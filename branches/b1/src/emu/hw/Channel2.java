package emu.hw;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.logging.Level;

import emu.os.ChannelTask;

/**
 * Channel 2 Implementation.
 * @author bjdrew@gmal.com
 *
 */
public class Channel2 extends Channel {

	/**
	 * The input file (card reader)
	 */
	BufferedWriter printer;
	
	private Channel2(int cycleTime, CPU cpu) {
		super(cycleTime, cpu);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Creates Channel 2
	 * @param cycleTime The number of cycles for channel 2
	 * @param cpu A CPU reference
	 * @param cardReader The input file.
	 */
	public Channel2(int cycleTime, CPU cpu, BufferedWriter printer) {
		super(cycleTime, cpu);
		this.printer = printer;
		
	}
	@Override
	void run() {
		trace.info("running channel 2, task:"+task);
		
		try {
			//Read block from input file (card reader) to the buffer.
			printer.write(task.getBuffer().getData());
			printer.newLine();
			printer.flush();
			//Update buffer status
			task.getBuffer().setEmpty();
			//Set CPU interrupt
			cpu.setIOi(CPU.Interrupt.IO_CHANNEL_2.getValue());

			trace.info("Buffer:"+task.getBuffer());
			
		} catch (IOException e) {
			//What TODO here?
			trace.log(Level.WARNING, "Failed to read from card reader.", e);
		}
		
		busy = false;
	}
	
	@Override
	public void start(ChannelTask task) {
		super.start(task);
		cpu.clearIOi(cpu.getIOi().getValue() - CPU.Interrupt.IO_CHANNEL_2.getValue());
	}
	
}
