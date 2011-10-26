package emu.hw;
/**
 * Interface for defining a memory device
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public interface MemoryUnit {
	String load(int addr) throws HardwareInterruptException;
	
	void store(int addr, String data) throws HardwareInterruptException;
	
	String read(int addr) throws HardwareInterruptException;
	
	void write(int addr, String data) throws HardwareInterruptException;
	
	
	void clear();
	
}
