package it.unive.lisa.cfg;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

/**
 * A descriptor of a CFG, containing the debug informations (source file, line,
 * column) as well as metadata
 * 
 * @author @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class CFGDescriptor {

	/**
	 * The source file where the CFG associated with this descriptor is defined. If
	 * it is unknown, this field might contain {@code null}.
	 */
	private final String sourceFile;

	/**
	 * The line where the CFG associated with this descriptor is defined in the
	 * source file. If it is unknown, this field might contain {@code -1}.
	 */
	private final int line;

	/**
	 * The column where the CFG associated with this descriptor is defined in the
	 * source file. If it is unknown, this field might contain {@code -1}.
	 */
	private final int col;

	/**
	 * The name of the CFG associated with this descriptor.
	 */
	private final String name;

	/**
	 * The names of the arguments of the CFG associated with this descriptor.
	 */
	private final String[] argNames;

	/**
	 * Builds the descriptor.
	 * 
	 * @param sourceFile the source file where the CFG associated with this
	 *                   descriptor is defined. If unknown, use {@code null}
	 * @param line       the line number where the CFG associated with this
	 *                   descriptor is defined in the source file. If unknown, use
	 *                   {@code -1}
	 * @param col        the column where the CFG associated with this descriptor is
	 *                   defined in the source file. If unknown, use {@code -1}
	 * @param name       the name of the CFG associated with this descriptor
	 * @param argNames   the names of the arguments of the CFG associated with this
	 *                   descriptor
	 */
	public CFGDescriptor(String sourceFile, int line, int col, String name, String... argNames) {
		Objects.requireNonNull(name, "The name of a CFG cannot be null");
		Objects.requireNonNull(argNames, "The array of argument names of a CFG cannot be null");
		for (int i = 0; i < argNames.length; i++)
			Objects.requireNonNull(argNames[i], "The " + i + "-th argument name of a CFG cannot be null");
		this.sourceFile = sourceFile;
		this.line = line;
		this.col = col;
		this.name = name;
		this.argNames = argNames;
	}

	/**
	 * Yields the source file name where the CFG associated with this descriptor is
	 * defined. This method returns {@code null} if the source file is unknown.
	 * 
	 * @return the source file, or {@code null}
	 */
	public final String getSourceFile() {
		return sourceFile;
	}

	/**
	 * Yields the line number where the CFG associated with this descriptor is
	 * defined in the source file. This method returns {@code -1} if the line number
	 * is unknown.
	 * 
	 * @return the line number, or {@code -1}
	 */
	public final int getLine() {
		return line;
	}

	/**
	 * Yields the column where the CFG associated with this descriptor is defined in
	 * the source file. This method returns {@code -1} if the line number is
	 * unknown.
	 * 
	 * @return the column, or {@code -1}
	 */
	public final int getCol() {
		return col;
	}

	/**
	 * Yields the name of the CFG associated with this descriptor.
	 * 
	 * @return the name of the CFG
	 */
	public String getName() {
		return name;
	}

	/**
	 * Yields the full name of the CFG associated with this descriptor. This might
	 * differ from its name (e.g. it might be fully qualified with the compilation
	 * unit it belongs to).
	 * 
	 * @return the full name of the CFG
	 */
	public String getFullName() {
		return name;
	}

	/**
	 * Yields the array containing the names of the arguments of the CFG associated
	 * with this descriptor.
	 * 
	 * @return the arguments names
	 */
	public String[] getArgNames() {
		return argNames;
	}

	@Override
	public String toString() {
		return name + "(" + StringUtils.join(argNames, ", ") + ") [at '" + String.valueOf(sourceFile) + "':" + line
				+ ":" + col + "]";
	}
}