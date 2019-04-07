/*
 * 1. Determine how Lox is receiving source code
 * 2. Scan source code either line by line (interactive) or as a file
 * 3. Parse tokens received from source code into usable data structures
 */

package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
	private static final Interpreter interpreter = new Interpreter();  // Use the same interpreter because of global variables
	static boolean hadError = false;
	static boolean hadRuntimeError = false;
	
	public static void main(String args[]) throws IOException {
		if (args.length > 1) {
			System.out.println("Usage: lox [script]");
			System.exit(64);
		} else if (args.length == 1) {
			runFile(args[0]);
		} else {
			runPrompt();
		}
	}
	
	private static void runFile(String path) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get(path));
		run(new String(bytes, Charset.defaultCharset()));
		
		// Indicate an error in the exit code
		if (hadError) System.exit(65);
		if (hadRuntimeError) System.exit(70);  // Why does this only matter for runFile and not runPrompt?
	}
	
	// Run an interactive user prompt
	private static void runPrompt() throws IOException {
		InputStreamReader input = new InputStreamReader(System.in);  // Stream reader that converts bytes to characters
		BufferedReader reader = new BufferedReader(input);  // Provides buffered reading of char stream
		
		for (;;) {  // Escape from interactive prompt with Ctrl-C
			System.out.print("> ");
			run(reader.readLine());
			hadError = false;
		}
	}
	
	private static void run(String source) {
		Scanner scanner = new Scanner(source);
		List<Token> tokens= scanner.scanTokens();
		
		Parser parser = new Parser(tokens);                    
		Expr expression = parser.parse();

	    // Stop if there was a syntax error.                   
	    if (hadError) return;                                  

	    interpreter.interpret(expression);
	}
	
	static void error(int line, String message) {
		report(line, "", message);
	}
	
	static void error(Token token, String message) {
		if (token.type == TokenType.EOF) {
			report(token.line, " at end", message);
		} else {
			report(token.line, " at '" + token.lexeme + "'", message);
		}
	}
	
	private static void report(int line, String where, String message) {
		System.err.println(
			"[line " + line + "] Error" + where + "; " + message
		);
		hadError = true;
	}
	
	static void runtimeError(RuntimeError error) {
		System.err.println(error.getMessage()) + "\n[line " + error.token.line + "]");
		hadRuntimeError = true;
	}
}
