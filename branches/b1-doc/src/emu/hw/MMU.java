package emu.hw;

import java.util.Random;
import java.util.logging.Logger;

import emu.hw.CPU.Interrupt;
import emu.util.Utilities;


/**
 * Memory Management Unit
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 */
public class MMU implements MemoryUnit {
	/**
	 * Tracer
	 */
	static Logger trace = Logger.getLogger("emuos");
	
	int pages = 10;

	private RAM ram;
	
	/**
	 * Constructor of Memory Management Unit
	 * @param size	of memory
	 * @param wordLength	length of word
	 * @param wordsInBlock	number of words in a block of memory
	 */
	public MMU(int size, int wordLength, int wordsInBlock) {
		ram = new RAM(size, wordLength, wordsInBlock);
	}
	
	/**
	 * Writes a string of data to the given logical address
	 * \dot digraph mmu_write {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * write_translateAddr[label="real address = translateAddr(logical address)"];
	 * write[label="store the data in the ram at the real address"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * write_begin [label="begin (logical address, data)", color="chartreuse"];
	 * write_return [label="return", color="firebrick1"];
	 * 
	 * write_begin -> write_translateAddr -> write -> write_return;
	 * } \enddot
	 * @param logicalAddr	to be written
	 * @param data	to be written to ram
	 */
	public void write(int logicalAddr,String data) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical addr to write page:"+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to write to:"+realAddr/10);
		ram.write(realAddr/10, data);
		//trace.finer(ram.readFrame(realAddr/10));
		trace.finer("<--");
	}
	
	/**
	 * \dot digraph mmu_writeFrame {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * writeFrame_translateAddr[label="real address = translateAddr(logical address)"];
	 * writeFrame[label="store the data in the ram at the real address"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * writeFrame_begin [label="begin (frame, data)", color="chartreuse"];
	 * writeFrame_return [label="return", color="firebrick1"];
	 * 
	 * writeFrame_begin -> writeFrame_translateAddr -> writeFrame -> writeFrame_return;
	 * } \enddot
	 * @param frame	number
	 * @param data	to be written
	 */
	public void writeFrame(int frame,String data) {
		trace.finer("-->");
		ram.write(frame, data);
		trace.finer("<--");
	}

	/**
	 * Reads a page from the given logical address
	 * \dot digraph mmu_read {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * read_translateAddr[label="real address = translateAddr(logical address)"];
	 * read[label="read a page from frame"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * read_begin [label="begin (logical address)", color="chartreuse"];
	 * read_return [label="return page", color="firebrick1"];
	 * 
	 * read_begin -> read_translateAddr -> read -> read_return;
	 * } \enddot
	 * @param logicalAddr to be read
	 * @returns	contents of frame at real address
	 */
	public String read(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical page to read: "+logicalAddr);
		int realAddr=translateAddr(logicalAddr);
		trace.finest("Real frame to read from: "+realAddr/10);
		trace.finer("<--");
		return ram.read(realAddr/10);
	}

	/**
	 * Reads a word from the given logical address
	 * \dot digraph mmu_load {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape=box,fontsize="8",fontname="Helvetica"];
	 * load_translateAddr[label="real address = translateAddr(logical address)"];
	 * load[label="load the data read address in ram"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * load_begin [label="begin (logical address)", color="chartreuse"];
	 * load_return [label="return word", color="firebrick1"];
	 * 
	 * load_begin -> load_translateAddr -> load -> load_return;
	 * } \enddot
	 * @param logicalAddr	to be loaded
	 * @returns contents of real address
	 */
	public String load(int logicalAddr) throws HardwareInterruptException {
		trace.finer("-->");
		trace.finest("Logical address to load from: "+logicalAddr);
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to load from: "+realAddr);
		trace.finer("<--");
		return ram.load(realAddr);
	}
	
	/**
	 * Stores the given word at the given logical address
	 * \dot digraph mmu_store {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * store_translateAddr[label="real address = translateAddr(logical address)"];
	 * store[label="store the data at real address in ram"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * store_begin [label="begin (logical address, data)", color="chartreuse"];
	 * store_return [label="return", color="firebrick1"];
	 * 
	 * store_begin -> store_translateAddr -> store -> store_return;
	 * } \enddot
	 * @param logicalAddr	to store data
	 * @param data	to store to read address
	 */
	public void store(int logicalAddr, String data) throws HardwareInterruptException {
		trace.finer("-->");
		int realAddr = translateAddr(logicalAddr);
		trace.finest("Real address to store to: "+realAddr);
		trace.finer("<--");
		ram.store(realAddr, data);
	}

	/**
	 * Dump the memory contents to a single string
	 */
	public String toString() {
		return ram.toString();
	}

	/**
	 * Translates a logical address (page*10+displacement) to a real address (frame*10+displacement)
	 * \dot digraph mmu_translateAddr {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * translateAddr_allocateFrame[label="frame = initPageTable()"];
	 * translateAddr_write[label="set frame contents to spaces"];
	 * translateAddr_time[label="check process time count and increment"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * translateAddr_begin [label="begin", color="chartreuse"];
	 * translateAddr_return [label="return frame", color="firebrick1"];
	 * 
	 * translateAddr_begin -> initPageTable_allocate -> initPageTable_write -> translateAddr_return;
	 * } \enddot
	 * @param logicalAddr
	 * @return A real address
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
		pageTable = ram.read(ptr);
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

	/**
	 * Initialize the page table 
	 * \dot digraph mmu_initPageTable {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * initPageTable_allocateFrame[label="frame = initPageTable()"];
	 * initPageTable_write[label="set frame contents to spaces"];
	 * allocateFrame_time[label="check process time count and increment"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * initPageTable_begin [label="begin", color="chartreuse"];
	 * initPageTable_return [label="return frame", color="firebrick1"];
	 * 
	 * initPageTable_begin -> initPageTable_allocate -> initPageTable_write -> allocateFrame_return;
	 * } \enddot
	 * 
	 * @return The physical frame where the page table will reside.
	 */
	public int initPageTable() {
		int frame = allocateFrame();
		String spaces = Utilities.padStringToLength(new String("")," ",40,false);
		// AMC: parse as integer fails for spaces. This indicates a page fault.  
		ram.write(frame,spaces);
		return frame; 
	}
	
	/**
	 * Free the frames pointed to by the page table and free the page table itself
	 * when a process is terminating
	 * \dot digraph mmu_freePageTable {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * node [shape="box",fontsize="8",fontname="Helvetica"];
	 * freePageTable_translateAddr[label="real address = translateAddr(logical address)"];
	 * freePageTable [label="store the data in the ram at the real address"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * freePageTable_begin [label="begin (logical address, data)", color="chartreuse"];
	 * freePageTable_return [label="return", color="firebrick1"];
	 * 
	 * freePageTable_begin -> freePageTable_translateAddr -> freePageTable -> freePageTable_return;
	 * } \enddot
	 */
	public void freePageTable() {
		//Get the page table frame # from PTR
		int PTR = CPU.getInstance().getPtr();
		//Read the current page table
		String pageTable = ram.read(PTR);
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
	
	/**
	 * Find a new frame in memory to use, return the frame
	 * \dot digraph mmu_allocateFrame {
	 * rankdir=TB; compound=true; nodesep=0.5; fontsize="8";
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape=box,fontsize="8",fontname="Helvetica"];
	 * allocateFrame_fetch[label="fetch"];
	 * allocateFrame_increment[label="increment"];
	 * allocateFrame_execute[label="execute"];
	 * allocateFrame_time[label="check process time count and increment"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * allocateFrame_begin [label="begin", color="chartreuse"];
	 * allocateFrame_return [label="return done", color="firebrick1"];
	 * 
	 * allocateFrame_begin -> allocateFrame_fetch-> allocateFrame_increment ->
	 * allocateFrame_execute -> allocateFrame_time -> allocateFrame_return;
	 * } \enddot
	 * @return The frame number.
	 */
	public int allocateFrame() {
		Random generator = new Random();
		int frameNum = 0;
		//We keep track of which frames are free in the ArrayList freeFrames
		//So to randomly select one of them, use the generator to grab a random int <= the size of the List
		//and select frameNum to be the int located at that randomly selected index
		//so we don't have to check to see if it's allocated and keep trying again. When there is only one free frame
		//that could take a long time and this is very fast.  
		frameNum = ram.getFreeFrames().get(generator.nextInt(ram.getFreeFrames().size()));
		trace.info("Frame allocated: "+frameNum);
		//Clear any residual data from the frame
		String spaces = Utilities.padStringToLength(new String("")," ",40,false);
		ram.write(frameNum,spaces);

		//Mark the frame we selected as allocated
		ram.markAllocated(frameNum);
		
		return frameNum;
	}
	
	@Override
	public void clear() {
		ram.clear();
	}
	
	/**
	 * Allocate frame for a given page number.
	 * \dot digraph mmu_allocatePage {
	 * rankdir=TB; compound=true; nodesep=0.5;
	 * 
	 * edge [fontname="Helvetica",fontsize="8",labelfontname="Helvetica",labelfontsize="8"];
	 * node [shape=box,fontsize="8",fontname="Helvetica"];
	 * allocatePage_allocateFrame[label="frame = allocateFrame()"];
	 * allocatePage_getPTR[label="get location of page table from page table register"];
	 * allocatePage_readPT[label="read page table"];
	 * allocatePage_zeros[label="pad page table entry with zeros"];
	 * allocatePage_write[label="write new entry to page table"];
	 * 
	 * node [shape="ellipse",style="filled",fontsize="8",fontname="Helvetica"];
	 * allocatePage_begin [label="begin (page number)", color="chartreuse"];
	 * allocatePage_return [label="return frame", color="firebrick1"];
	 * 
	 * allocatePage_begin -> allocatePage_allocateFrame -> allocatePage_getPTR ->
	 * allocatePage_readPT -> allocatePage_zeros -> allocatePage_write -> allocatePage_return;
	 * } \enddot
	 * @param pageNumber	given page number
	 * @return The frame number
	 */
	public int allocatePage(int pageNumber) {
		//Allocate a frame. Frame # returned
		int frame = allocateFrame();
		//Update page table entry.
		//Get the page table frame #
		int pageTableFrame = CPU.getInstance().getPtr();
		//Read the current page table
		String pageTable = ram.read(pageTableFrame);
		//
		//pad the new entry with leading zeroes
		String newEntry = Utilities.padStringToLength(new Integer(frame).toString(), "0", 4, true);
		//stick the new entry in place
		String updatedPageTable = pageTable.substring(0,pageNumber*4) + newEntry + pageTable.substring((pageNumber+1)*4);
		trace.fine("PageTable: " +updatedPageTable);
		ram.write(pageTableFrame,updatedPageTable);
		CPU.getInstance().setPtl(Math.max(CPU.getInstance().getPtl(),pageNumber+1));
		trace.info("page->frame : "+pageNumber+"->"+frame);
		return frame;
	}
	
}
