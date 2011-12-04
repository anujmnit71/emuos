package emu.hw;

import java.util.Random;
import java.util.logging.Logger;

import emu.hw.CPUState.Interrupt;
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

	private static RAM ram;
	private static Drum drum;
	private PageTable PT;
	
	/**
	 * Self Reference 
	 */
	static MMU ref;

	public MMU() {
		ram = new RAM();
		drum = new Drum();
	}

	public static MMU getInstance() {

		if (ref == null) {
			ref = new MMU();
		}

		return ref;
	}
	
	/* 
	 * Writes a string of data to the given logical address
	 * @param logicalAddr
	 * @param data
	 */
	public void write(int ptr, int logicalAddr,String data) throws HardwareInterruptException {
//		trace.finer("-->");
		trace.finest("Logical addr to write page:"+logicalAddr);
		int realAddr=translateAddr(ptr,logicalAddr);
		trace.finest("Real frame to write to:"+realAddr/10);
		ram.write(ptr,realAddr/10, data);
		// set the dirty bit for this page
		PT.getEntry(logicalAddr/10).setDirty();
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		PT.storePageTable(ptr);
//		trace.finer("<--");
	}

	public void writeFrame(int ptr,int frame,String data) throws HardwareInterruptException {
//		trace.finer("-->");
		ram.write(ptr,frame, data);
//		trace.finer("<--");
	}

	/*
	 * Reads a page from the given logical address
	 * @param logicalAddr
	 * @returns String
	 */
	public String read(int ptr, int logicalAddr) throws HardwareInterruptException {
//		trace.finer("-->");
		trace.finest("Logical page to read: "+logicalAddr);
		int realAddr=translateAddr(ptr,logicalAddr);
		trace.finest("Real frame to read from: "+realAddr/10);
		trace.finer("<--");
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		PT.storePageTable(ptr);
		return ram.read(ptr,realAddr/10);
	}

	/*
	 * Reads a word from the given logical address
	 * @param logicalAddr
	 * @returns String
	 */
	public String load(int ptr, int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical address to load from: "+logicalAddr);
		setPageTable(ptr);
		int realAddr = translateAddr(ptr,logicalAddr);
		trace.finest("Real address to load from: "+realAddr);
		trace.finer("<--");
		//Read the current page table	
		PT = getPageTable(ptr);
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
//		ram.write(0, ptr, PT.toString());
		PT.storePageTable(ptr);
		return ram.load(ptr,realAddr);
	}

	private void setPageTable(int ptr) {
		PT = getPageTable(ptr);
	}

	/**
	 * Stores the given word at the given logical address
	 * @param logicalAddr
	 * @param data
	 */
	public void store(int ptr, int logicalAddr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		int realAddr = translateAddr(ptr,logicalAddr);
		trace.finest("Real address to store to: "+realAddr);
		trace.finer("<--");
		// set the dirty bit for this page
		PT.getEntry(logicalAddr/10).setDirty();
		// save page in LRU
		PT.setLRU(PT.getEntry(logicalAddr/10));
		// write the changes to the page table to memory
		PT.storePageTable(ptr);
		ram.store(ptr,realAddr, data);
	}

	/**
	 * Translates a logical address (page*10+displacement) and returns a real address (frame*10+displacement)
	 * @param logicalAddr
	 * @return
	 */
	private int translateAddr(int ptr, int logicalAddr)
			throws HardwareInterruptException {
		int logicalPageNum = logicalAddr / 10;
		int displacement = logicalAddr % 10;
		Integer frameNum;
		trace.finer("-->");
		// Get page table from memory. Note that this fills in variable PT with
		// the page table.
		// It is used in read/write/load/store to set the LRU/dirty bit of the
		// entry affected
		PT = getPageTable(ptr);
		//trace.info(PT.getPageTable());
		trace.finest("LogicalAddr: " + logicalAddr + "; Logical Page@: "
				+ logicalPageNum + "; Displacement: " + displacement);
		// Determine page fault
		// If the page is swapped

		if (PT.getEntry(logicalPageNum).isSwapped()) {
			CPU.getInstance().setPi(Interrupt.PAGE_FAULT);
			throw new HardwareInterruptException();
		}
		//or if it has never been loaded
		try {
			frameNum = PT.getEntry(logicalPageNum).getBlockNum();
		} catch (NumberFormatException e) {
			trace.finer("NumberFormatException:"+e.getMessage());
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
		ram.write(0,frame, PT.toString());
		return frame;
	}

	/**
	 * Free the frames pointed to by the page table and free the page table itself
	 * when a process is terminating
	 */
	public void freePageTable() {
		//Read the current page table			
		PageTable PT = getPageTable();
		freePageTable(PT);
	}
	/**
	 * Free the page table at the given frame
	 * @param ptFrame
	 */
	public void freePageTable(int ptFrame) {
		freePageTable(getPageTable(ptFrame));
	}
	/**
	 * Free the given PageTable
	 * @param PT
	 */
	public void freePageTable(PageTable PT) {
		int frameNum = 0;
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
		ram.write(0,frameNum,spaces);
		//Mark the frame we selected as allocated
		ram.markAllocated(frameNum);
		return frameNum;
	}

	/**
	 * Find a new track on the drum to use, return the track
	 * @return The track number.
	 */
	public int allocateTrack() {
		Random generator = new Random();
		try {
			int trackNum = drum.getFreeTracks().get(generator.nextInt(drum.getFreeTracks().size()));
			trace.info("track allocated: "+trackNum);
			//Clear any residual data from the frame
			String spaces = Utilities.padStringToLength(new String("")," ",40,false);
			drum.write(0,trackNum,spaces);
			//Mark the frame we selected as allocated
			drum.markAllocated(trackNum);
			return trackNum;
		}
		catch (IllegalArgumentException e) {
			trace.severe("*** Drum is full");
			trace.severe(drum.toString());
		}
		return 9999;
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
		//Read the current page table
		PT = getPageTable();
		trace.finer("Pages in memory: "+PT.pagesInMemory());
		//If we haven't already allocated 4 frames in memory (page table plus 3 frames
		if (PT.pagesInMemory() < pagesAllowedInMemory) { 
			System.out.println("Allocating a page");
			// Allocate a frame. Frame # returned
			int frame = allocateFrame();
			// Update page table entry.
			//Read the current page table
//			PT = getPageTable();
			//stick the new frame number in the correct PTE
			PT.getEntry(pageNumber).setBlockNum(frame);
			//indicate that it's been recently used
			PT.setLRU(PT.getEntry(pageNumber));
			// store the updated page table
			PT.storePageTable(CPU.getInstance().getPtr());
			// update the PTL to the total number of pages in memory
			CPU.getInstance().setPtl(Math.min(CPU.getInstance().getPtl() + 1, pagesAllowedInMemory));
			trace.info("page->frame : " + pageNumber + "->" + frame);
			return frame;
		}
		// else we have used up all 4 and we need to swap out the LRU one
		else {
			System.out.println("Swapping");
			//Swap(pageNumber);
		}
		return 99;
	}

	public RAM getRam() {
		return ram;
	}

	public Drum getDrum() {
		return drum;
	}

	public String toString() {
		return ram.toString() + drum.toString();	
		//		return ram.toString();	
	}
	/**
	 * Checks if the page fault is valid based on the IR
	 * @return
	 */
	public boolean validatePageFault(int ptr,String ir) {
		trace.info("Validating page fault for "+ir);
//		setPageTable(ptr);
		if (ir == null
				|| ir.startsWith(CPU.GET)
				|| ir.startsWith(CPU.STORE)
				|| ir.startsWith(CPU.BRANCH)) {
			trace.info("valid page fault on IR="+ir);
			return true;
		}
		else if (ir.startsWith(CPU.PUT)
				|| ir.startsWith(CPU.LOAD)
				|| ir.startsWith(CPU.COMPARE)) {
			int targetPage = Integer.parseInt(ir.substring(2,3));
			System.out.println("Page: "+targetPage);
			PT = getPageTable(ptr);
			System.out.println("PTE: "+PT.getEntry(targetPage).toString());
			if (PT.getEntry(targetPage).isSwapped()) {
				trace.info("valid page fault on IR="+ir);
				return true;
			}
			else {
				trace.info("invalid page fault on IR="+ir+", page "+PT.getEntry(targetPage)+" not swapped!");
			}
		}
		else {
			trace.info("invalid page fault on IR="+ir);
		}
		return false;
	}
	/**
	 * Get the current page table in use by the CPU
	 * @return
	 */
	public PageTable getPageTable() {
		int pageTableFrame = CPU.getInstance().getPtr();
		//trace.info("pageTableFrame = " + pageTableFrame);
		//Read the current page table
		return getPageTable(pageTableFrame);
	}
	/**
	 * Get the page table stored at the given frame
	 * @param frame
	 * @return
	 */
	public PageTable getPageTable(int frame) { 
		return new PageTable(ram.read(0,frame));
	}

	private PageTable getNewPageTable() {
		return new PageTable();
	}

	public void Swap(int ptr, int newPage) {
		// Find victim page: LRU[3]
		int victimPage = PT.getVictim();
		// Find victim frame to swap out
		int victimFrame = PT.getEntry(victimPage).getBlockNum();
		trace.info("swapping page "+newPage+", victimPage="+victimPage+", victimFrame="+victimFrame);
		// if dirty bit for victim page is on, call proc SwapOut to swap out victim
		if (PT.getEntry(victimPage).isDirty())
			SwapOut(ptr, victimPage,victimFrame);
		PT.getEntry(victimPage).setSwap();
		// call proc SwapIn to swap in the new page
		SwapIn(ptr,newPage,victimFrame);
		PT.storePageTable(ptr);
	}

	public void SwapIn(int ptr, int newPage, int frame) {
		// write new page to specified frame if it's on the drum -- this will get queued on the swap queue
		if (PT.getEntry(newPage).isSwapped())
			ram.write(ptr,frame, drum.read(ptr,PT.getEntry(newPage).getBlockNum()));
		// clear swapped/dirty bits in PTE
		PT.getEntry(newPage).setDirty(false);
		PT.getEntry(newPage).setSwap(false);
		// write frame num to PTE
		PT.getEntry(newPage).setBlockNum(frame);
	}

	public void SwapOut(int ptr,int victimPage, int frame) {
		// Get a free drum track
		Random generator = new Random();
		int drumTrack = drum.getFreeTracks().get(generator.nextInt(drum.getFreeTracks().size()));
		// read victim page from specified frame
		String victimPageData = ram.read(ptr,frame);
		// write victim page to drum track -- this will get queued up on the swap queue
		drum.write(ptr, drumTrack, victimPageData);
		// write drum track to PTE
		PT.getEntry(victimPage).setBlockNum(drumTrack);
		// clear dirty bit in PTE
		PT.getEntry(victimPage).setDirty(false);
	}

	public int getFrame(int ptr, int logicalAddr) throws HardwareInterruptException {
		return translateAddr(ptr, logicalAddr) / 10;
	}
}
