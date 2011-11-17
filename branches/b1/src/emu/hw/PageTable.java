package emu.hw;

public class PageTable {
	

	private final int entries = 10;
	private PageTableEntry[] pageTable;
	
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

}
