package emu.hw;
/**
 * Interface for defining a memory device
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public interface MemoryUnit {
	String load(int ptr,int addr) throws HardwareInterruptException;
	
	void store(int ptr, int addr, String data) throws HardwareInterruptException;
	
	String read(int ptr, int addr) throws HardwareInterruptException;
	
	void write(int ptr, int addr, String data) throws HardwareInterruptException;

	void clear();
	
}
