package emu.util;

public class Utilities {
	/**
	 * Pad the given string with the given character until the string reaches the given length
	 * @param s String to pad
	 * @param c Character to pad with
	 * @param l Desired length of string 
	 * @param f Pad to front
	 * @return The padded string
	 */
	public static String padStringToLength (String s, String c, int l, boolean f) {
		if (s == null){
			s = "null";
		}
		if (s.length() < l){
	        while (s.length() < l){
	        	if (f) {
	        		s = c+s;
	        	}
	        	else {
	        		s = s+c;
	        	}
	        }
		}
		else {
			s = s.substring(0, l);
		}
		return s;
	}
}
