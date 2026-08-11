# Minijava Semantic Checker

## About
Minijava is a subset of java. This program is written in (actual) Java. It parses minijava files (like the file Example.java)
and runs semantic checks on it, such as type checking, which pass if and only if the file is a valid minijava file. To do this,
we use the visitor pattern to explore the syntax tree. 

## Building
how to build: cd into the directory with the makefile (minijava) and run "make".
This will generate code with JTB and JavaCC and compile the code in src/.
To run, you can use "make run", which will test the program on the file Example.java.

## Related
JavaCC is a parser generator. JTB is used to build a syntax tree for the parser, which we then explore with the
visitor pattern. The parser is generated from the grammar in minijava.jj
[(BNF version here)](http://cgi.di.uoa.gr/~thp06/project_files/minijava-new/minijava.html). 