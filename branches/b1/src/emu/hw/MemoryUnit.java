package emu.hw;

public interface MemoryUnit {
	String load(int addr) throws HardwareInterruptException;
	
	void store(int addr, String data) throws HardwareInterruptException;
	
	String readFrame(int addr) throws HardwareInterruptException;
	
	void writeFrame(int addr, String data) throws HardwareInterruptException;
	
	
	void clear();
	
}
