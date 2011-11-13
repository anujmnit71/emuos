package emu.hw;
/**
 * Interface for defining a memory device
 * @author b.j.drew@gmail.com
 * @author willaim.mosley@gmail.com
 * @author claytonannam@gmail.com
 *
 */
public interface MemoryUnit {
	String load(int addr);
	
	void store(int addr, String data);
	
	String read(int addr);
	
	void write(int addr, String data);

	void clear();
	
}
