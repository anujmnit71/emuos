package emu.hw;

import java.util.logging.Logger;

public class PageTable {
	
	static Logger trace = Logger.getLogger("emuos");

	private final int entries = 10;
	private PageTableEntry[] pageTable;
	
	/**
	 * Self Reference 
	 */
	static PageTable ref;
	
	public PageTable(String pTable) {
		pageTable = new PageTableEntry[entries];
		for (int i = 0; i < entries; i++) {
			pageTable[i] = new PageTableEntry(pTable.substring(i*4,(i+1)*4));
		}
	}
	
	public PageTable() {
		pageTable = new PageTableEntry[entries];
		for (int i=0; i<entries; i++) {
			pageTable[i] = new PageTableEntry();
		}
	}
	
	private PageTable getInstance() {

		if (ref == null) {
			ref = new PageTable();
		}

		return ref;
	}
	
	public String getPageTable() {
		String returnPage = new String();
		for (int i=0; i<entries; i++) {
			returnPage += pageTable[i].toString();
		}
		return returnPage;
	}
	
	public PageTableEntry getEntry(int entryNum) {
		return pageTable[entryNum];
	}
	
	public void setEntry(PageTableEntry newEntry, int entryNum) {
		pageTable[entryNum] = newEntry;
	}
	
	public int getVictim() {
		for (int i=0; i < entries; i++) {
			if (pageTable[i].getLRU() == 3)
				return i;
		}
		System.out.println("Error in getVictim");
		return 0;
	}
	
	public void setLRU(PageTableEntry recentEntry) {
		if (recentEntry.getLRU() == 1)
			return;
		if (recentEntry.getLRU() == 2) {
			for (int i = 0; i < entries; i++) {
				if (pageTable[i].getLRU() == 1)
					pageTable[i].setLRU(2);
			}
			recentEntry.setLRU(1);
			return;
		}
		if (recentEntry.getLRU() == 3) {
			for (int i = 0; i < entries; i++) {
				if (pageTable[i].getLRU() == 2)
					pageTable[i].setLRU(3);
				else if (pageTable[i].getLRU() == 1)
					pageTable[i].setLRU(2);
			}
			recentEntry.setLRU(1);
			return;
		}
		for (int i = 0; i < entries; i++) {
			if (pageTable[i].getLRU() == 3)
				pageTable[i].setLRU(0);
			else if (pageTable[i].getLRU() == 2)
				pageTable[i].setLRU(3);
			else if (pageTable[i].getLRU() == 1)
				pageTable[i].setLRU(2);
		}
		recentEntry.setLRU(1);
		return;
	}
	
	public String toString() {
		String tempString = new String();
		for (int i=0; i<entries; i++) {
			tempString += pageTable[i].toString();
		}
		return tempString;
	}
	
	public void storePageTable() {
		storePageTable(-1);
	}
	
	public void storePageTable(int ptr) {
		int pageTableFrame = ptr;
		if (pageTableFrame < 0)
			pageTableFrame = CPU.getInstance().getPtr();
		
		    //trace.info("pageTableFrame = " + pageTableFrame);
			trace.finest("Storing page table: "+toString()+" at frame: "+pageTableFrame);
			MMU.getInstance().getRam().write(0,pageTableFrame, toString());
	}

	public void storePageTable(int ptr,PageTable pt) {
		pt.getInstance().storePageTable(ptr);
	}
	
	public void storePageTable(PageTable pt) {
		pt.getInstance().storePageTable(-1);
	}
}
