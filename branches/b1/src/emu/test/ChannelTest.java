package emu.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

import emu.hw.Buffer;
import emu.hw.CPU;
import emu.hw.Channel1;
import emu.hw.Channel2;
import emu.hw.HardwareInterruptException;
import emu.os.ChannelTask;

public class ChannelTest {
	static Logger trace = Logger.getLogger("emuos");
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			channel12Test();
		} catch (HardwareInterruptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void channel12Test() throws HardwareInterruptException {
		// TODO Auto-generated method stub

		CPU cpu = CPU.getInstance();
		
		BufferedReader input = null;
		BufferedWriter output = null;
		
		try {
			input = new BufferedReader(new FileReader("/home/bdrew/emuos/test"));
			output  = new BufferedWriter(new FileWriter("/home/bdrew/emuos/outputTest"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Channel1 ch1 = new Channel1(3,cpu,input);
		Channel2 ch2 = new Channel2(4,cpu,output);
		
		Buffer b = new Buffer();
		ChannelTask task1 = new ChannelTask();
		task1.setBuffer(b);
		task1.setType(ChannelTask.TaskType.INPUT_SPOOLING);
		ch1.start(task1);
		
		ChannelTask task2 = new ChannelTask();
		task2.setBuffer(b);
		task2.setType(ChannelTask.TaskType.OUTPUT_SPOOLING);
		ch2.start(task2);
		
		for (int i = 0; i <= 6; i++) {
			trace.info(i+"****************************************************************************");
			
			ch1.increment();
			ch2.increment();

			trace.info("CPU:"+cpu);
		}
		
		if (!ch1.isBusy()) {
			trace.info("restart ch1");
			task1.setBuffer(new Buffer());
			ch1.start(task1);
		}
		
	}

}
