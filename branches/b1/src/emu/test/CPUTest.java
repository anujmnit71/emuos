package emu.test;

import emu.hw.CPU;
import emu.hw.CPUState;

public class CPUTest {
	static CPU cpu = CPU.getInstance();
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//cpu.toString();
		//cloneTest();
		CPUState cpus = new CPUState();
		
		System.out.println(cpu.toString());
		
	}

//	private static void cloneTest() {
//		
//		
//		cpu.increment();
//		
//		CPUState cpu1 = cpu.getCPUState();
//		
//		cpu1.setIOi(7);
//		
//		CPUState cpu2 = null;
//		try {
//			cpu2 = (CPUState) cpu.getCPUState().clone();
//		} catch (CloneNotSupportedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		cpu1.clearIOi(4);
//		cpu1.setIr("1234");
//		cpu1.setC(true);
//		
//		System.out.println(cpu1.toString());
//		System.out.println(cpu2.toString());
//
//		
//	}

}
