package emu.hw;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Level;

import emu.hw.CPUState.Interrupt;
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
		trace.info("running channel 1, task:"+task.getType());
		
		try {
			//Read block from input file (card reader)
			String data = cardReader.readLine();
			if (data.length() > 40) {
				data = data.substring(0,40);
			}
			//Write to the buffer
			task.getBuffer().setData(data);
			//Update buffer status
			task.getBuffer().setInputFull();
			//TODO CPU needs support for the
			cpu.setIOi(Interrupt.IO_CHANNEL_1.getValue());

			trace.info("Buffer:"+task.getBuffer());
		} catch (IOException e) {
			//What TODO here?
			trace.log(Level.WARNING, "Failed to read from card reader.", e);
		} catch (NullPointerException e) {
			trace.info("End of input");
		}
		
		busy = false;
	}

	@Override
	public void start(ChannelTask task) throws HardwareInterruptException {
		super.start(task);
		trace.info("starting channel 1");
		//cpu.clearIOi(Interrupt.IO_CHANNEL_1.getValue());
	}

	@Override
	public
	void close() throws IOException {
		cardReader.close();		
	}

}
