package emu.hw;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Level;

import emu.os.ChannelTask;

/**
 * Channel 1 Implementation.
 * @author bjdrew@gmal.com
 *
 */
public class Channel1 extends Channel {

	/**
	 * The input file (card reader)
	 */
	BufferedReader cardReader;
	
	private Channel1(int cycleTime, CPU cpu) {
		super(cycleTime, cpu);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Creates Channel 1
	 * @param cycleTime The number of cycles for channel 1
	 * @param cpu A CPU reference
	 * @param cardReader The input file.
	 */
	public Channel1(int cycleTime, CPU cpu, BufferedReader cardReader) {
		super(cycleTime, cpu);
		this.cardReader = cardReader;
		
	}
	@Override
	void run() {
		trace.info("running channel 1, task:"+task);
		
		try {
			//Read block from input file (card reader) to the buffer.
			task.getBuffer().setData(cardReader.readLine());
			//Update buffer status
			task.getBuffer().setInputFull();
			//TODO CPU needs support for the
			cpu.setIOi(CPU.Interrupt.IO_CHANNEL_1.getValue());

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
		cpu.clearIOi(CPU.Interrupt.IO_CHANNEL_1.getValue());
	}

}
