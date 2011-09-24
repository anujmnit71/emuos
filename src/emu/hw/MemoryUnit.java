package emu.hw;

public interface MemoryUnit {
	String load(int addr) throws HardwareInterruptException;
	
	void store(int addr, String data) throws HardwareInterruptException;
	
	String readBlock(int addr) throws HardwareInterruptException;
	
	void writeBlock(int addr, String data) throws HardwareInterruptException;
	
	void clear();
	
}
