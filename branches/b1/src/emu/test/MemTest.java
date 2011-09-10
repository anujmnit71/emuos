package emu.test;

import emu.hw.MMU;

public class MemTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MMU mmu = new MMU();
		mmu.store(0, "LR12");
		mmu.store(20, "PD12");
		mmu.store(99, "GD02");
		mmu.writeBlock(34, "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJ");
		
		System.out.println(mmu.readBlock(32));
		
		System.out.println(mmu.toString());

	}

}
