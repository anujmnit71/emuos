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
import emu.hw.Channel3;
import emu.hw.HardwareInterruptException;
import emu.os.ChannelTask;
import emu.os.Kernel;

public class ChannelTest {
	static Logger trace = Logger.getLogger("emuos");
	static CPU cpu;
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		
		Kernel.initTrace(args);
		
		cpu = CPU.getInstance();
		
		try {
			//channel12Test();
			inputSpoolTest();
			outputSpoolTest();
		} catch (HardwareInterruptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("RAM:");
		System.out.println(cpu.getMMU().getRam().toString());
		
		System.out.println("Drum:");
		System.out.println(cpu.getMMU().getDrum().toString());
	}

	/**
	 * Raw test to read a block of data from a file and pass it to ch3 to write it to the drum.
	 * @throws HardwareInterruptException
	 */
	private static void inputSpoolTest() throws HardwareInterruptException {
		// TODO Auto-generated method stub

		BufferedReader input = null;
		
		try {
			input = new BufferedReader(new FileReader("/home/bdrew/emuos/test"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Create Channels
		Channel1 ch1 = new Channel1(3,cpu,input);
		Channel3 ch3 = new Channel3(2,cpu);
		
		//Create a buffer
		Buffer b = new Buffer();
		
		//Create a task
		ChannelTask task1 = new ChannelTask();
		task1.setBuffer(b);
		task1.setType(ChannelTask.TaskType.INPUT_SPOOLING);
		
		//Start ch1
		ch1.start(task1);
		
		//arbitrary loop
		while (true) {
			trace.info("****************************************************************************");
			trace.info("CPU:"+cpu);
			
			ch1.increment();
			ch3.increment();

			//check for channel 1
			if (cpu.getIOi().getValue() == 1) {
				trace.info("ch1 1 done");
				//create ch3 task, start channel.
				ChannelTask task3 = new ChannelTask();
				//Get buffer from ch1
				task3.setBuffer(ch1.getTask().getBuffer());
				task3.setType(ChannelTask.TaskType.INPUT_SPOOLING);
				task3.setTrack(20);
				ch3.start(task3);
				
				//Clear ch1 interrupt
				cpu.clearIOi(1);
			}
			
			//When channel 3 is complete, break
			if (cpu.getIOi().getValue() == 4) {
				trace.info("ch1 3 done");
				break;
			}
			
		}
		
	}
	
	/**
	 * Raw test to read a block of data from a file and pass it to ch3 to write it to the drum.
	 * @throws HardwareInterruptException
	 */
	private static void outputSpoolTest() throws HardwareInterruptException {
		// TODO Auto-generated method stub

		BufferedWriter output = null;
		
		try {
			output  = new BufferedWriter(new FileWriter("/home/bdrew/emuos/outputTest"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Create Channels
		Channel3 ch3 = new Channel3(2,cpu);
		Channel2 ch2 = new Channel2(4,cpu,output);
		
		//Create a buffer
		Buffer b = new Buffer();
		//b.setData(Utilities.padStringToLength("TESTDATA", "-", 40, false));
		
		//Create a task for ch 3
		ChannelTask task3 = new ChannelTask();
		task3.setBuffer(b);
		task3.setType(ChannelTask.TaskType.OUTPUT_SPOOLING);
		task3.setTrack(20);

		//Start ch3
		ch3.start(task3);
		
		//arbitrary loop
		while (true) {
			trace.info("****************************************************************************");
			trace.info("CPU:"+cpu);
			
			ch2.increment();
			ch3.increment();

			if (cpu.getIOi().getValue() == 4) {
				trace.info("ch1 3 done");

				ChannelTask task2= new ChannelTask();
				//Get buffer from ch3
				task2.setBuffer(ch3.getTask().getBuffer());
				task2.setType(ChannelTask.TaskType.OUTPUT_SPOOLING);
				
				ch2.start(task2);

				//Clear ch3 interrupt
				cpu.clearIOi(4);
			}
			
			//end when channel 2 is complete
			if (cpu.getIOi().getValue() == 2) {
				trace.info("ch1 2 done");
				break;
			}
			
		}
		
	}
	
	/**
	 * Raw test that uses ch1 to read a line from a file, then use ch2 to write it to another file.
	 * @throws HardwareInterruptException
	 */
	private static void channel12Test() throws HardwareInterruptException {

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
