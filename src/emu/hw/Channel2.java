package emu.hw;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.logging.Level;

import emu.hw.CPUState.Interrupt;
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
			String data = task.getBuffer().getData();
			trace.info("printing:'"+data+"'");
			printer.write(data);
			printer.newLine();
			printer.flush();
			//Update buffer status
			task.getBuffer().setEmpty();
			//Set CPU interrupt
			cpu.setIOi(Interrupt.IO_CHANNEL_2.getValue());

			trace.info("Buffer:"+task.getBuffer());
			
		} catch (IOException e) {
			//What TODO here?
			trace.log(Level.WARNING, "Failed to read from card reader.", e);
		}
		
		busy = false;
	}
	
	@Override
	public void start(ChannelTask task) throws HardwareInterruptException {
		super.start(task);
		trace.info("starting channel 2");
		trace.fine(task.toString());
	}

	@Override
	public void close() throws IOException {
		printer.close();		
	}
	
}
