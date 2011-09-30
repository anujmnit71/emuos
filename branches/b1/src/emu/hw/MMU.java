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
	
	int pages = 10;

	private RAM ram;
	
	public MMU(int size, int wordLength, int wordsInBlock) {
		ram = new RAM(size, wordLength, wordsInBlock);
	}
	
	/* 
	 * Writes a string of data to the given frame number
	 * @param frame
	 * @param data
	 */
	public void writeFrame(int frame, String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest(""+frame);
		ram.writeFrame(frame, data);
		//trace.finer(ram.readFrame(frame));
		trace.finer("<--");
	}
	
	public void writePage(int logicalAddr,String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical addr to write page:"+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to write to:"+realAddr/10);
		ram.writeFrame(realAddr/10, data);
		//trace.finer(ram.readFrame(realAddr/10));
		trace.finer("<--");
	}

	/*
	 * Reads a frame from the given frame number
	 * @param frame
	 * @returns String
	 */
	public String readFrame(int frame) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest(""+frame);
		trace.finer("<--");
		return ram.readFrame(frame);
	}
	
	public String readPage(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical page to read: "+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to read from: "+realAddr/10);
		trace.finer("<--");
		return ram.readFrame(realAddr/10);
	}

	/*
	 * Reads a word from the given real address
	 * @param realAddr
	 * @returns String
	 */
	public String load(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical address to load from: "+logicalAddr);
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to load from: "+realAddr);
		trace.finer("<--");
		return ram.load(realAddr);
	}
	
	
	
	public String loadVirt(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical address to load from: "+logicalAddr);
		int realAddr = translateAddr(logicalAddr);
		trace.finer("<--");
		return load(realAddr);
	}

	/**
	 * Stores the given word at the given real address
	 * @param realAddr
	 * @param data
	 */
	public void store(int logicalAddr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to load from: "+realAddr);
		trace.finer("<--");
		ram.store(realAddr, data);
	}

	public String toString() {
		return ram.toString();
	}

	/**
	 * Translates a logical address (page*10+displacement) and returns a real address (frame*10+displacement)
	 * @param logicalAddr
	 * @return
	 */
	private int translateAddr(int logicalAddr) throws HardwareInterruptException{
		int ptr;
		int logicalPageNum = logicalAddr/10;
		int displacement = logicalAddr%10;
		Integer frameNum;
		String pageTable;
		
		trace.finer("-->");
		//Get PTR from CPU
		ptr = CPU.getInstance().getPtr();
		trace.finest("LogicalAddr: "+logicalAddr+"; Logical Page@: "+logicalPageNum+"; Displacement: "+displacement);
		
		//Determine page fault 
		pageTable = ram.readFrame(ptr);
		String pageTableEntry = pageTable.substring(logicalPageNum*4,(logicalPageNum+1)*4);
		trace.finest("Page Table:"+pageTable+"; Page Table Entry: "+pageTableEntry);
		
		try {
			frameNum = new Integer(pageTableEntry);
		}
		catch (NumberFormatException e) {
			//Does page being referenced have a frame allocated for it?
			trace.warning("page fault on addr "+logicalAddr);
			CPU.getInstance().setPi(Interrupt.PAGE_FAULT);
			trace.finer("<--");
			throw new HardwareInterruptException();
		}
		
		int realAddr = frameNum*10+displacement;
		trace.info("logical->real : "+logicalAddr+"->"+realAddr);
		
		trace.finer("<--");
		return realAddr;
		
	}
	//TODO pageIsAllocated(page) method

	/**
	 * Initialize the page table 
	 * @return The physical frame where the page table will reside.
	 */
	public int initPageTable() {
		//At this point, all this needs to do is allocate memory, right? 
		int frame = allocateFrame();
		String zeroes = Utilities.padStringToLength(new String("")," ",40,false);
		// AMC: parse as integer fails for spaces. This indicates a page fault.  
		try {
		ram.writeFrame(frame,zeroes);
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
		//Get the page table frame # from PTR
		int PTR = CPU.getInstance().getPtr();
		try {
		//Read the current page table
		String pageTable = ram.readFrame(PTR);
		//Free the frames referenced in the page table
		for (int i=0; i<ram.wordsInBlock; i++) {
			try {
				String pageTableEntry = pageTable.substring(i*4,(i+1)*4);
				int frameNum = new Integer(pageTableEntry);
				ram.markFree(frameNum);
			}
			catch (NumberFormatException e) {
				trace.finest(i + "wasn't backed by a frame");
			}
		}
		//Free the frame backing the page table
		ram.markFree(PTR);
		//Set the PTL to zero
		CPU.getInstance().setPtl(0);
		}
		catch (HardwareInterruptException e) {
			trace.severe("Pagetable should be readable and writable");
		}
	}
	
	/**
	 * Find a new frame in memory to use, return the frame
	 * @return The frame number.
	 */
	public int allocateFrame() {
		//Generate random number between 0 and a numFrames 
		//Validate that it is not already allocated, if so try until we get a valid frame.
		Random generator = new Random();
		int frameNum = 0;
//		while (frameNum == 0) { 
//			frameNum = generator.nextInt(30);
//			if (ram.isAllocated(frameNum)) {
//				frameNum=0;
//				trace.info(frameNum+" is already allocated. Choosing again ...");
//			}
//		}
		//We keep track of which frames are free in the ArrayList freeFrames
		//So to randomly select one of them, use the generator to grab a random int <= the size of the List
		//and select frameNum to be the int located at that randomly selected index
		//so we don't have to check to see if it's allocated and keep trying again. When there is only one free frame
		//that could take a long time and this is very fast.  
		frameNum = ram.getFreeFrames().get(generator.nextInt(ram.getFreeFrames().size()));
		trace.info("Frame allocated: "+frameNum);
		//Mark the frame we selected as allocated
		ram.markAllocated(frameNum);
		
		return frameNum;
	}
	
	/**
	 * Writes boot sector to first block, initiates program load.
	 * @param bootSector
	 */
	public void writeBootSector(String bootSector) {
		try {
			ram.writeFrame(0, bootSector);
		} catch (HardwareInterruptException e) {
			trace.severe("shouldnt be here");
		}
	}

	@Override
	public void clear() {
		ram.clear();
	}
	
	/**
	 * 
	 * @param pageNumber
	 * @return The frame number
	 */
	public int allocatePage(int pageNumber) {
		//Allocate a frame. Frame # returned
		int frame = allocateFrame();
		//Update page table entry.
		//Get the page table frame #
		int pageTableFrame = CPU.getInstance().getPtr();
		try {
		//Read the current page table
		String pageTable = ram.readFrame(pageTableFrame);
		//
		//pad the new entry with leading zeroes
		String newEntry = Utilities.padStringToLength(new Integer(frame).toString(), "0", 4, true);
		//stick the new entry in place
		String updatedPageTable = pageTable.substring(0,pageNumber*4) + newEntry + pageTable.substring((pageNumber+1)*4);
		trace.fine("PageTable: " +updatedPageTable);
		ram.writeFrame(pageTableFrame,updatedPageTable);
		}
		catch (HardwareInterruptException e) {
			trace.severe("Pagetable should be readable and writable");
		}
		CPU.getInstance().setPtl(Math.max(CPU.getInstance().getPtl(),pageNumber+1));
		trace.info("page->frame : "+pageNumber+"->"+frame);
		return frame;
	}
	
}
