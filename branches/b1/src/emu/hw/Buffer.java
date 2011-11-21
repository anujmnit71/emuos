package emu.hw;

import java.util.Random;

import emu.os.State;

/**
 * Consumed by Channels. Encapsulates a block of data.
 * @author bjdrew@gmail.com
 *
 */
public class Buffer {
	/**
	 * All possible Buffer states
	 * @author bjdrew@gmail.com
	 *
	 */
	public enum BufferState {
		EMPTY ("empty"),
		INPUT_FULL ( "inputFull"),
		OUTPUT_FULL( "outputFull");  
		String stateName;
		BufferState (String stateName) {
			this.stateName = stateName;
		}
		public String getStateName() {
			return stateName;
		}
	}
	int id;
	/**
	 * Current state
	 */
	State state;
	/**
	 * The block of data. 
	 */
	String data;
	
	/**
	 * Default Constructor, defaults to an empty buffer.
	 */
	public Buffer() {
		Random generator = new Random();
		id = generator.nextInt();
		state = new State();
		state.setCurrent(BufferState.EMPTY.getStateName());
	}
	public State getState() {
		return state;
	}
	public void setState(State state) {
		this.state = state;
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	} 
	
	public boolean isEmpty() {
		return state.getCurrent().equals(BufferState.EMPTY.getStateName());
	}
	public boolean isInputFull() {
		return state.getCurrent().equals(BufferState.INPUT_FULL.getStateName());
	}

	public boolean isOutputFull() {
		return state.getCurrent().equals(BufferState.OUTPUT_FULL.getStateName());
	}

	public void setEmpty() {
		state.setCurrent(BufferState.EMPTY.getStateName());
	}
	public void setInputFull() {
		state.setCurrent(BufferState.INPUT_FULL.getStateName());
	}

	public void setOutputFull() {
		state.setCurrent(BufferState.OUTPUT_FULL.getStateName());
	}

	
	public String toString() {
		return "[ Buffer :: id="+id+", data="+data+", state="+state+" ]";
	}

}
