package emu.util;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
/**
 * Parse LogRecords to a single line
 * @author b.j.drew@gmail.com
 *
 */
public class TraceFormatter extends Formatter {

	SimpleDateFormat sd = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss.SSS");
	@Override
	public String format(LogRecord l) {
		String line = "";
//		String line = sd.format(new Date(l.getMillis())) + " | ";
//		if (l.getSourceClassName() != null) {
//			line += Utilities.padStringToLength(l.getSourceClassName(), " ", 12, false) +" | ";			
//		}
//		if (l.getSourceMethodName() != null) {
//			line += Utilities.padStringToLength(l.getSourceMethodName(), " ", 10, false)+" | ";			
//		}
//		if (!l.getLevel().equals(Level.INFO)) {
//			line +=  l.getLevel() + ": ";			
//		}
		line += l.getMessage();		
		
		if (l.getThrown() != null) {
			line += " | "+exception(l.getThrown());
		}
		else {
			line+=" \n";

		}
		return line;
	}
	/**
	 * Prints all nested stack traces.
	 * @param e
	 * @return
	 */
	private String exception(Throwable e) {
		String a = Arrays.toString(e.getStackTrace());
		a = a.replaceAll("\\[", "\tat ");
		a = a.replaceAll("\\]", "\n");
		a = e.getMessage()+"\n"+e.toString()+":\n"+a.replaceAll(",", "\n\tat");
		if (e.getCause() != null) {
			a+="Caused By:\n";
			try {
				a+=exception((Exception)e.getCause());				
			} catch (Exception ee) {}
		}
		return a;
	}
}
