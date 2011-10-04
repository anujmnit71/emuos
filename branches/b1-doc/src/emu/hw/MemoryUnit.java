package emu.hw;

public interface MemoryUnit {
	String load(int addr) throws HardwareInterruptException;
	
	void store(int addr, String data) throws HardwareInterruptException;
	
	String read(int addr) throws HardwareInterruptException;
	
	void write(int addr, String data) throws HardwareInterruptException;
	
	void clear();
	
	public String toString();	
}
