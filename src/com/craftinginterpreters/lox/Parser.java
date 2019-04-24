/* Uses recursive descent parsing to parse a list of tokens generated by the scanner.
 * Each expression type evaluates to either a flat sequence of the same expression type
 * or an expression of higher precedence (e.g. addition evalutes to a chain of additions
 * or to a multiplication, which may evaluate into a unary, which may evaluate into a
 * primary)
 */

package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Literal;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
	private static class ParseError extends RuntimeException {}
	
	private final List<Token> tokens;
	private int current = 0;
	private final int max_args = 8;  // Can probably raise by a lot
	
	Parser(List<Token> tokens) {
		this.tokens = tokens;
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
			// var and func declarations go here because declarations statements are only legal
			// in a few places, and not (for example) in the middle of an expression
			if (match(VAR)) return varDeclaration();
			if (match(FUN)) return function("function");
			
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

	private Stmt whileStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'while'.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after condition.");
		Stmt body = statement();

		return new Stmt.While(condition, body);
	}
	
	private Stmt statement() {
		if (match(FOR)) return forStatement();
		if (match(IF)) return ifStatement();
		if (match(PRINT)) return printStatement();
		if (match(RETURN)) return returnStatement();
		if (match(WHILE)) return whileStatement();
		if (match(LEFT_BRACE)) return new Stmt.Block(block());
		
		return expressionStatement();
	}

	// No separate ASDT node for "for" statements since it's just sugar
	private Stmt forStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'for'.");

		// for (;;) is valid syntax
		Stmt initializer;
		if (match(SEMICOLON)) {
			initializer = null;
		} else if (match(VAR)) {
			initializer = varDeclaration();
		} else {
			initializer = expressionStatement();
		}

		Expr condition = null;
		if (!check(SEMICOLON)) {
			condition = expression();
		}
		consume(SEMICOLON, "Expect ';'after loop condition.");

		Expr increment = null;
		if (!check(RIGHT_PAREN)) {
			increment = expression();
		}
		consume(RIGHT_PAREN, "Expect ')'after for clauses.");

		Stmt body = statement();

		// If there is an increment, execute it each iteration after the main body
		if (increment != null) {
			body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
		}

		// If there's no condition supplied to for loop, execute indefinitely
		if (condition == null) condition = new Expr.Literal(true);
		body = new Stmt.While(condition, body);

		// If initializer is provided, execute that statement/expression before the body
		if (initializer != null) {
			body = new Stmt.Block(Arrays.asList(initializer, body));
		}

		return body;
	}

	private Stmt ifStatement() {
		consume(LEFT_PAREN, "Expect '(' after if.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after if condition");

		Stmt thenBranch = statement();
		Stmt elseBranch = null;
		if (match(ELSE)) {
			elseBranch = statement();
		}

		return new Stmt.If(condition, thenBranch, elseBranch);
	}
	
	private Stmt printStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Print(expr);
	}

	private Stmt returnStatement() {
		Token keyword = previous();
		Expr value = null;
		if (!check(SEMICOLON)) {  // Return can return a value or nothing at all;
			value = expression();
		}

		consume(SEMICOLON, "Expect ';' after return value");
		return new Stmt.Return(keyword, value);
	}
	
	private Stmt expressionStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Expression(expr);
	}

	// Syntax for defining a function (vs call, which is syntax for calling a function)
	private Stmt.Function function(String kind) {  // kind can be function or method
		Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
		consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
		List<Token> parameters = new ArrayList<>();

		if (!check(RIGHT_PAREN)) {
			do {
				if (parameters.size() >= max_args) {
					error(peek(), "Cannot have more than " + max_args + " parameters");
				}

				parameters.add(consume(IDENTIFIER, "Expect parameter name"));
			} while (match(COMMA));
		}
		consume(RIGHT_PAREN, "Expect ')'after parameters.");
		consume(LEFT_BRACE, "Expect { before " + kind + " body.");
		List<Stmt> body = block();
		return new Stmt.Function(name, parameters, body);
	}

	private List<Stmt> block() {
		List<Stmt> statements = new ArrayList<>();

		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after block.");
		return statements;
	}
	
	private Expr expression() {
		return assignment();
	}
	
	private Expr assignment() {                                   
		Expr expr = or();

		if (match(EQUAL)) {                                         
			Token equals = previous();                                
			Expr value = assignment();                                

			if (expr instanceof Expr.Variable) {                      
				Token name = ((Expr.Variable)expr).name;                
				return new Expr.Assign(name, value);                    
			}                                                         

			error(equals, "Invalid assignment target."); 
	    }                                                           

	    return expr;                                                
	}

	private Expr or() {
		Expr expr = and();

		while (match(OR)) {
			Token operator = previous();
			Expr right = and();
			expr = new Expr.Logical(expr, operator, right);
		}

		return expr;
	}

	private Expr and() {
		Expr expr = equality();

		while (match(AND)) {
			Token operator = previous();
			Expr right = equality();
			expr = new Expr.Logical(expr, operator, right);
		}

		return expr;
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
		
		return call();
	}

	private Expr call() {
		Expr expr = primary();  // Function identifier is a primary, found using the same logic as finding variables. Saved as Expr.Variable node (which will be the callee of Expr.Call node)

		while (true) {  // Can be any number of consecutive function calls
			if (match(LEFT_PAREN)) {
				expr = finishCall(expr);
			} else {
				break;
			}
		}

		return expr;
	}

	private Expr finishCall(Expr callee) {
		List<Expr> arguments = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (arguments.size() >= max_args) {
					error(peek(), "Cannot have more than " + max_args + " arguments");
				}
				arguments.add(expression());
			} while (match(COMMA));
		}

		Token paren = consume(RIGHT_PAREN, "Expect ')'after arguments.");

		return new Expr.Call(callee, paren, arguments);
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
}    
