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
	
	public void writeBlock(int frame, String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine(""+frame);
//		int pa = translateAddr(frame); /* AMC: ?? */
		ram.writeBlock(frame, data);
		trace.info(ram.readBlock(frame));
		trace.finer("<--");
	}

	public String readBlock(int frame) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine(""+frame);
//		int pa = translateAddr(frame);
		trace.finer("<--");
		return ram.readBlock(frame);
	}

	public String load(int addr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine(""+addr);
		int pa = translateAddr(addr);
		trace.finer("<--");
		return ram.load(pa);
	}

	public void store(int addr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.fine(""+addr);
		int pa = translateAddr(addr);
		trace.finer("<--");
		ram.store(pa, data);
	}

	public String toString() {
		// TODO Auto-generated method stub
		return super.toString();
	}

	/**
	 * Translates a logical address
	 * @param logicalAddr
	 * @return
	 */
	private int translateAddr(int logicalAddr) throws HardwareInterruptException{
		int PTR;
		int logicalPageNum;
		String pageTable;
		
		trace.finer("-->");
		//TODO Get PTR from CPU
		PTR = CPU.getInstance().getPtr();
		//TODO Extract page # from logical addr
		logicalPageNum = logicalAddr/10;
		trace.info("LogicalAddr: "+logicalAddr);
		trace.info("Logical Page#: "+logicalPageNum);
		
		//TODO Determine page fault (for real)
			pageTable = ram.readBlock(PTR);
			trace.info("Page Table:"+pageTable);
			
				
		boolean pageFault = logicalAddr != 0;
		if (pageFault) {
			trace.warning("page fault on addr "+logicalAddr);
		//	CPU.getInstance().setPi(Interrupt.PAGE_FAULT);
			trace.finer("<--");
			throw new HardwareInterruptException();
		}
		
		//TODO Use page # as offset to PTR, this yields a physical addr
		//TODO Load frame #, multiply * 10 and add to logical displacement
		trace.finer("<--");
		return 0;
	}

	/**
	 * Initialize the page table 
	 * @return The physical address that the page table will reside.
	 */
	public int initPageTable() {
		//At this point, all this needs to do is allocate memory, right? 
		int frame = allocateFrame();
		String zeroes = Utilities.padStringToLength(new String(""),"0",40,false);
		try {
		ram.writeBlock(frame,zeroes);
		} catch (HardwareInterruptException e) {
			trace.severe("Error in init page table");
		}
		return frame; /* AMC: this used to return allocateFrame()*10 .. I think this was an error */
	}
	
	/**
	 * Find a new frame in memory to use, return the frame
	 * @return The frame number.
	 */
	public int allocateFrame() {
		//TODO Generate random number between 0 and a numFrames 
		//TODO Validate that it is not already allocated, if so try until we get a valid frame.
		Random generator = new Random();
		int frameNum = 0;
		while (frameNum == 0) { 
			frameNum = generator.nextInt(30);
			if (ram.isAllocated(frameNum)) {
				frameNum=0;
				trace.info(frameNum+" is already allocated. Choosing again ...");
			}
		}
		trace.info("Frame to allocate: "+frameNum);
		ram.markAllocated(frameNum);
		
		return frameNum;
	}
	
	/**
	 * Writes boot sector to first block, initiates program load.
	 * @param bootSector
	 */
	public void writeBootSector(String bootSector) {
		try {
			ram.writeBlock(0, bootSector);
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
		String pageTable = ram.readBlock(pageTableFrame);
		//
//		Integer frameInt = new Integer(frame);
		//pad the new entry with leading zeroes
		String newEntry = Utilities.padStringToLength(new Integer(frame).toString(), "0", 4, true);
		//stick the new entry in place
		String updatedPageTable = pageTable.substring(0,pageNumber*4) + newEntry + pageTable.substring((pageNumber+1)*4);
		trace.info("PageTable: " +updatedPageTable);
		ram.writeBlock(pageTableFrame,updatedPageTable);
		}
		catch (HardwareInterruptException e) {
			trace.severe("Pagetable should be readable and writable");
		}
		CPU.getInstance().setPtl(CPU.getInstance().getPtl()+1);
		return frame;
	}
	
	public RAM getRAM() {
		return ram;
	}
	
}
