/* Uses recursive descent parsing to parse a list of tokens generated by the scanner.
 * Each expression type evaluates to either a flat sequence of the same expression type
 * or an expression of higher precedence (e.g. addition evalutes to a chain of additions
 * or to a multiplication, which may evaluate into a unary, which may evaluate into a
 * primary)
 */

package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Literal;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
	private static class ParseError extends RuntimeException {}
	
	private final List<Token> tokens;
	private int current = 0;
	
	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}
	
	private Expr expression() {
		return equality();
	}
	
	// Corresponds to equality → comparison ( ( "!=" | "==" ) comparison )* 
	// Leftmost comparison nonterminal is evaluated first, followed by the
	// rightmost 0+ instances of actual equality checks (represented by the
	// while loop)
	private Expr equality() {
		Expr expr = comparison();
		
		while (match(BANG_EQUAL, EQUAL_EQUAL)) {
			Token operator = previous();
			Expr right = comparison();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
	}
	
	private Expr comparison() {
		Expr expr = addition();
		
		while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expr right = addition();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
	}
	
	private Expr addition() {
		Expr expr = multiplication();
		
		while (match(PLUS, MINUS)) {
			Token operator = previous();
			Expr right = multiplication();
			expr = new Expr.Binary(expr,  operator,  right);
		}
		
		return expr;
	}
	
	private Expr multiplication() {
		Expr expr = unary();
		
		while (match(STAR, SLASH)) {
			Token operator = previous();
			Expr right = unary();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
	}
	
	private Expr unary() {
		if (match(BANG, MINUS)) {
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}
		
		return primary();
	}
	
	private Expr primary() {
		if (match(FALSE)) return new Expr.Literal(false);
		if (match(TRUE)) return new Expr.Literal(true);
		if (match(NIL)) return new Expr.Literal(null);
		
		if (match(NUMBER, STRING)) {
			return new Expr.Literal(previous().literal);
		}
		
		if (match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expect ')'after expression.");
			return new Expr.Grouping(expr);
		}
				
		if (match(IDENTIFIER)) {
			return new Expr.Variable(previous());
		}
		
		throw error(peek(), "Expect expression.");
	}
	
	// Check if next token matches any type in types
	private boolean match(TokenType... types) {
		for (TokenType type : types) {
			if (check(type)) {
				advance();
				return true;
			}
		}
		
		return false;
	}
	
	private boolean check(TokenType type) {
		if (isAtEnd()) return false;
		return peek().type == type;
	}
	
	private Token advance() {
		if (!isAtEnd()) current++;
		return previous();
	}
	
	private boolean isAtEnd() {
		return peek().type == EOF;
	}
	
	private Token peek() {
		return tokens.get(current);
	}
	
	private Token previous() {
		return tokens.get(current - 1);
	}
	
	// Consume next token if it matches type, else throw an error
	private Token consume(TokenType type, String message) {
		if (check(type)) return advance();
		
		throw error(peek(), message);
	}
	
	private ParseError error(Token token, String message) {
		Lox.error(token, message);
		return new ParseError();
	}
	
	private void synchronize() {
		advance();
		
		while (!isAtEnd()) {
			if (previous().type == SEMICOLON) return;
			
			switch(peek().type) {
				case CLASS:
				case FUN:                              
		        case VAR:                              
		        case FOR:                              
		        case IF:                               
		        case WHILE:                            
		        case PRINT:                            
		        case RETURN:                           
		          return;
			}
			
			advance();
		}
	}
	
	List<Stmt> parse() {                
	    List<Stmt> statements = new ArrayList<>();
	    while (!isAtEnd()) {
	    	statements.add(declaration());
	    }
	    
	    return statements;
	}         
	
	// If in the middle of parsing the next statement, the parser runs into an error, it will
	// synchronize - that is, skip over successive tokens until it gets to a statement delimiter,
	// usually a semicolon, but also a keyword that signifies the start of a new statement, such
	// as class, if, return, etc. Nothing is returned for the statement on which parsing failed,
	// and parsing continues on the next statement
	private Stmt declaration() {
		try {
			if (match(VAR)) return varDeclaration();
			
			return statement();
		} catch (ParseError error) {
			synchronize();
			return null;
		}
	}
	
	private Stmt varDeclaration() {
		Token name = consume(IDENTIFIER, "Expect variable name.");
		
		Expr initializer = null;
		if (match(EQUAL)) {
			initializer = expression();
		}
		
		consume(SEMICOLON, "Expect ';' after variable declaration.");
		return new Stmt.Var(name, initializer);
	}
	
	private Stmt statement() {
		if (match(PRINT)) return printStatement();
		
		return expressionStatement();
	}
	
	private Stmt printStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Print(expr);
	}
	
	private Stmt expressionStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Expression(expr);
	}
}    
