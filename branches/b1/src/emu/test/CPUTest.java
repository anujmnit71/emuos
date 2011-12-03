package emu.test;

import emu.hw.CPU;
import emu.hw.CPUState;
import emu.os.PCB;

public class CPUTest {
	static CPU cpu = CPU.getInstance();
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//cpu.toString();
		//cloneTest();
		try {
			contextSwitchTest();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//System.out.println(cpu.toString());
		
	}
	private static void contextSwitchTest() throws CloneNotSupportedException {
		
		PCB currentPcb = new PCB("C", 10, 10);
		PCB nextPcb = new PCB("N", 10, 10);
		nextPcb.setCpuState(new CPUState());

		cpu.setIr("CURR");
		currentPcb.setCpuState((CPUState) cpu.getCPUState().clone());
		System.out.println(cpu.toString());
		//cpu.setState(nextPcb.getCpuState());
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
