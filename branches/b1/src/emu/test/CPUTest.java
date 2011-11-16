package emu.test;

import emu.hw.CPU;

public class CPUTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		CPU cpu1 = CPU.getInstance();
		cpu1.increment();
		cpu1.setIOi(7);
		
		CPU cpu2 = null;
		try {
			cpu2 = (CPU) cpu1.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		cpu1.clearIOi(4);
		cpu1.setIr("1234");
		cpu1.setC(true);
		
		System.out.println(cpu1.toString());
		System.out.println(cpu2.toString());
		
	}

}
