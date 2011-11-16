package emu.os;

/**
 * Class to represent a state transition
 * @author bjdrew@gmail.com
 *
 */
public class State {
	/**
	 * Current State
	 */
	String current;
	/**
	 * Next State
	 */
	String next;
	
	public String getCurrent() {
		return current;
	}
	public void setCurrent(String current) {
		this.current = current;
	}
	public String getNext() {
		return next;
	}
	public void setNext(String next) {
		this.next = next;
	}
	public String toString() {
		return "[ State :: current="+current+", next="+next+" ]";
	}
}
