package emu.hw;

/**
 * CPU Data Structure
 * @author b.j.drew@gmail.com
 *
 */
public class CPU {
	
	/**
	 * Instruction Register
	 */
	String ir;
	/**
	 * General Register
	 */
	String gr;
	/**
	 * Toggle 
	 */
	boolean c;
	/**
	 * Instruction counter
	 */
	int ic;
	/**
	 * System interrupt
	 */
	Interupt si;
	
	public enum Interupt {
	    READ, WRITE, TERMINATE
	}
	
	public String getIr() {
		return ir;
	}

	public void setIr(String ir) {
		this.ir = ir;
	}

	public boolean isC() {
		return c;
	}

	public void setC(boolean c) {
		this.c = c;
	}

	public int getIc() {
		return ic;
	}

	public void setIc(int ic) {
		this.ic = ic;
	}

	public Interupt getSi() {
		return si;
	}

	public void setSi(Interupt si) {
		this.si = si;
	}

	public int getIrValue() {
		// TODO Auto-generated method stub
		return 0;
	}
}
