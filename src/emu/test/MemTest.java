package emu.test;

import emu.hw.CPU;
import emu.hw.HardwareInterruptException;
//import emu.hw.RAM;
import emu.hw.MMU;
//import emu.hw.Drum;

public class MemTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MMU mmu = new MMU();
		CPU cpu = CPU.getInstance();
		try {
			cpu.setPtr(mmu.initPageTable());
			mmu.allocatePage(0);
			mmu.store(0, "LR12");
			mmu.allocatePage(2);
			mmu.store(20, "PD12");
			mmu.allocatePage(9);
			mmu.store(99, "GD02");
			mmu.allocatePage(3);
			mmu.write(34, "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJ");
			mmu.allocatePage(4);
			mmu.write(40, "AnnaWillBrendon");
			mmu.validatePageFault("PD01"); //Valid
			mmu.validatePageFault("GD70"); //valid
			mmu.validatePageFault("LR50"); //invalid
			mmu.validatePageFault("LR00"); //valid
			mmu.validatePageFault("SR84"); //valid
			mmu.validatePageFault("BT01"); //valid
			mmu.validatePageFault("CR20"); //valid
			
			System.out.println(mmu.read(32));
			System.out.println(mmu.load(99));
			try {
				mmu.store(2, "LR21");
			}
			catch (HardwareInterruptException e) {
				if (mmu.validatePageFault("SR02"))
					mmu.Swap(0);
				try {
					mmu.store(2, "LR21");
				}
				catch (HardwareInterruptException f) {
					System.out.println("Pagefault validate failed");
				}
			}
		} catch (HardwareInterruptException e) {
			// TODO Auto-generated catch block
			System.out.println("Page fault caught");
		}

		System.out.println(mmu.toString());

	}

}
