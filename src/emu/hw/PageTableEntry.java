package emu.hw;

public class PageTableEntry {
	
	private String dirtySwap;
	private String LRU;
	private String blockNum;
	
	public PageTableEntry(String entry) { 
		dirtySwap = entry.substring(0,1);
		LRU = entry.substring(1,2);
		blockNum = entry.substring(2);
	}
	
	public PageTableEntry(int frame) { 
		dirtySwap = " ";
		LRU = "0";
		blockNum = String.format("%02d", frame);
	}
	
	public PageTableEntry() {
		dirtySwap = " ";
		LRU = "0";
		blockNum = "  ";
	}
	
	public boolean isInMemory() {
		try {
			getBlockNum();
			if (isSwapped()) return false;
		}
		catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
	
	public int getLRU() {
		try {
			return Integer.parseInt(LRU);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}
	
	public void setLRU(int newLRU) {
		LRU = ""+newLRU;
	}
	
	public int getBlockNum() throws NumberFormatException {
		return Integer.parseInt(blockNum);
	}
	
	public void setDirty(boolean set) {
		if (set == true)
			dirtySwap = "D";
		else if (set == false)
			dirtySwap = " ";
	}
	
	public void setDirty() {
		dirtySwap = "D";
	}
	
	public void setSwap(boolean set) {
		if (set == true)
			dirtySwap = "S";
		else if (set == false)
			dirtySwap = " ";
	}
	
	public void setSwap() {
		dirtySwap = "S";
	}
	
	public void setBlockNum(int set) {
		blockNum = ""+String.format("%02d",set);
	}
	
	public boolean isDirty() {
		return (dirtySwap.equals("D"));
	}
	
	public boolean isSwapped() {
		return (dirtySwap.equals("S"));
	}
	
	public String toString() {
//		System.out.println("PTE::"+dirtyBit+swapBit+String.format("%02d", blockNum));
		return ""+dirtySwap+LRU+blockNum;
	}
}
