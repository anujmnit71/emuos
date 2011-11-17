package emu.hw;

import java.util.Random;
import java.util.logging.Logger;

import emu.hw.CPU.Interrupt;
import emu.util.Utilities;


/**
 * Memory Management Unit
 *
 */
public class MMU implements MemoryUnit {
	/**
	 * Tracer
	 */
	static Logger trace = Logger.getLogger("emuos");
	
	protected final int numPages = 10;
	protected final int pagesAllowedInMemory = 3; /* 4 are allowed but one will always be the page table */

	private RAM ram;
	private Drum drum;
	private PageTable PT;
	
	public MMU() {
		ram = new RAM();
		drum = new Drum();
	}
	
	/* 
	 * Writes a string of data to the given logical address
	 * @param logicalAddr
	 * @param data
	 */
	public void write(int logicalAddr,String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical addr to write page:"+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to write to:"+realAddr/10);
		ram.write(realAddr/10, data);
		// set the dirty bit for this page
		PT.getEntry(logicalAddr/10).setDirty();
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		storePageTable();
		trace.finer("<--");
	}
	
	public void writeFrame(int frame,String data) throws HardwareInterruptException {
		trace.finer("-->");
		ram.write(frame, data);
		trace.finer("<--");
	}

	/*
	 * Reads a page from the given logical address
	 * @param logicalAddr
	 * @returns String
	 */
	public String read(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical page to read: "+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to read from: "+realAddr/10);
		trace.finer("<--");
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		storePageTable();
		return ram.read(realAddr/10);
	}

	/*
	 * Reads a word from the given logical address
	 * @param logicalAddr
	 * @returns String
	 */
	public String load(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical address to load from: "+logicalAddr);
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to load from: "+realAddr);
		trace.finer("<--");
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		storePageTable();
		return ram.load(realAddr);
	}
	
	/**
	 * Stores the given word at the given logical address
	 * @param logicalAddr
	 * @param data
	 */
	public void store(int logicalAddr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to store to: "+realAddr);
		trace.finer("<--");
		// set the dirty bit for this page
		PT.getEntry(logicalAddr/10).setDirty();
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		storePageTable();
		ram.store(realAddr, data);
	}

	/**
	 * Translates a logical address (page*10+displacement) and returns a real address (frame*10+displacement)
	 * @param logicalAddr
	 * @return
	 */
	private int translateAddr(int logicalAddr)
			throws HardwareInterruptException {
		int logicalPageNum = logicalAddr / 10;
		int displacement = logicalAddr % 10;
		Integer frameNum;
		trace.finer("-->");
		// Get page table from memory. Note that this fills in variable PT with
		// the page table.
		// It is used in read/write/load/store to set the LRU/dirty bit of the
		// entry affected
		PT = getPageTable();
		trace.finest("LogicalAddr: " + logicalAddr + "; Logical Page@: "
				+ logicalPageNum + "; Displacement: " + displacement);
		// Determine page fault
		try {
			frameNum = PT.getEntry(logicalPageNum).getBlockNum();
		} catch (NumberFormatException e) {
			// Does page being referenced have a frame allocated for it?
			trace.warning("page fault on addr " + logicalAddr);
			CPU.getInstance().setPi(Interrupt.PAGE_FAULT);
			trace.finer("<--");
			throw new HardwareInterruptException();
		}
		int realAddr = frameNum * 10 + displacement;
		trace.info("logical->real : " + logicalAddr + "->" + realAddr);
		trace.finer("<--");
		return realAddr;
	}

	/**
	 * Initialize the page table 
	 * @return The physical frame where the page table will reside.
	 */
	public int initPageTable() {
		int frame = allocateFrame();
		PT = getNewPageTable();
		try {
			ram.write(frame, PT.toString());
		} catch (HardwareInterruptException e) {
			trace.severe("Error in init page table");
		}
		return frame;
	}
	
	/*
	 * Free the frames pointed to by the page table and free the page table itself
	 * when a process is terminating
	 */
	public void freePageTable() {
		int frameNum = 0;
		//Read the current page table			
		PT = getPageTable();
		trace.finest("Page table: "+PT.toString());
	
		//Free the frames referenced in the page table
		for (int i=0; i<numPages; i++) {
			try {
				frameNum = PT.getEntry(i).getBlockNum();
				ram.markFree(frameNum);
			}
			catch (NumberFormatException e) {
				trace.finest(i + " wasn't backed by a frame");
			}
		}
		//Free the frame backing the page table
		ram.markFree(CPU.getInstance().getPtr());
		//Set the PTL to zero
		CPU.getInstance().setPtl(0);
	}
	
	/**
	 * Find a new frame in memory to use, return the frame
	 * @return The frame number.
	 */
	public int allocateFrame() {
		Random generator = new Random();
		//We keep track of which frames are free in the ArrayList freeFrames
		//So to randomly select one of them, use the generator to grab a random int <= the size of the List
		//and select frameNum to be the int located at that randomly selected index
		//so we don't have to check to see if it's allocated and keep trying again. When there is only one free frame
		//that could take a long time and this is very fast.  
		int frameNum = ram.getFreeFrames().get(generator.nextInt(ram.getFreeFrames().size()));
		trace.info("Frame allocated: "+frameNum);
		//Clear any residual data from the frame
		String spaces = Utilities.padStringToLength(new String("")," ",40,false);
		try {
			ram.write(frameNum,spaces);
		} catch (HardwareInterruptException e) {
			trace.severe("Error in allocating a new frame");
		}
		//Mark the frame we selected as allocated
		ram.markAllocated(frameNum);
		return frameNum;
	}
	
	@Override
	public void clear() {
		ram.clear();
		drum.clear();
	}
	
	/**
	 * 
	 * @param pageNumber
	 * @return The frame number
	 */
	public int allocatePage(int pageNumber) {
		// Allocate a frame. Frame # returned
		int frame = allocateFrame();
		// Update page table entry.
		//Read the current page table
		PT = getPageTable();
		//stick the new frame number in the correct PTE
		PT.getEntry(pageNumber).setBlockNum(frame);
		// store the updated page table
		storePageTable();
		// update the PTL to the total number of pages in memory
		CPU.getInstance().setPtl(Math.max(CPU.getInstance().getPtl() + 1, pagesAllowedInMemory));
		trace.info("page->frame : " + pageNumber + "->" + frame);
		return frame;
	}
	
	public RAM getRam() {
		return ram;
	}
	
	public Drum getDrum() {
		return drum;
	}
	
	public String toString() {
		return ram.toString();	
	}
	/**
	 * Checks if the page fault is valid based on the IR
	 * @return
	 */
	public boolean validatePageFault(String ir) {
		trace.info("***Validating page fault for "+ir);
		if (ir == null 
				|| ir.startsWith(CPU.GET)
				|| ir.startsWith(CPU.STORE)) {
			trace.info("valid page fault on IR="+ir);
			return true;
		}
		else {
			trace.severe("invalid page fault on IR="+ir);
			return false;
		}
	}
	private PageTable getPageTable() {
		int pageTableFrame = CPU.getInstance().getPtr();
		try {
		//Read the current page table
			return new PageTable(ram.read(pageTableFrame));
		}
		catch (HardwareInterruptException e) {
			trace.severe("Page table should be readable");
		}
		return new PageTable();
	}
	private PageTable getNewPageTable() {
		return new PageTable();
	}
	private void storePageTable() {
		int pageTableFrame = CPU.getInstance().getPtr();
		try {
			trace.finest("Storing page table: "+PT.toString()+" at frame: "+pageTableFrame);
			ram.write(pageTableFrame, PT.toString());
		}
		catch (HardwareInterruptException e) {
			trace.severe("Page table should be updatable");
		}
	}
	private void Swap(int newPage) {
		// Find victim page: LRU[3]
		// Find victim frame to swap out
		// if dirty bit for victim page is on, call proc SwapOut to swap out victim
		// call proc SwapIn to swap in the new page
	}
	
	private void SwapIn(int newPage, int frame) {
		// write new page to specified frame
		// clear swapped/dirty bits in PTE
		// write frame num to PTE
	}
	private void SwapOut(int victimPage, int frame) {
		// Get a free drum track
		// read victim page from specified frame
		// write victim page to drum track
		// write drum track to PTE
		// set swapped bit in PTE
		// clear dirty bit in PTE
	}
}
