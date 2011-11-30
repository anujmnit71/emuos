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
			int ptr = mmu.initPageTable();
			cpu.setPtr(ptr);
			mmu.allocatePage(0);
			mmu.store(ptr,0, "LR12");
			mmu.allocatePage(2);
			mmu.store(ptr,20, "PD12");
			mmu.allocatePage(9);
			mmu.store(ptr,99, "GD02");
			mmu.allocatePage(3);
			mmu.write(ptr,34, "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJ");
			mmu.allocatePage(4);
			mmu.write(ptr,40, "AnnaWillBrendon");
			mmu.validatePageFault(ptr,"PD01"); //Valid
			mmu.validatePageFault(ptr,"GD70"); //valid
			mmu.validatePageFault(ptr,"LR50"); //invalid
			mmu.validatePageFault(ptr,"LR00"); //valid
			mmu.validatePageFault(ptr,"SR84"); //valid
			mmu.validatePageFault(ptr,"BT01"); //valid
			mmu.validatePageFault(ptr,"CR20"); //valid
			
			System.out.println(mmu.read(ptr,32));
			System.out.println(mmu.load(ptr,99));
			try {
				mmu.store(ptr,2, "LR21");
			}
			catch (HardwareInterruptException e) {
				if (mmu.validatePageFault(ptr,"SR02"))
					mmu.Swap(ptr,0);
				try {
					mmu.store(ptr,2, "LR21");
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
